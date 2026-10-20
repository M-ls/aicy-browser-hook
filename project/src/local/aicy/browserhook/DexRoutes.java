package local.aicy.browserhook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 按方法签名和字节码字符串查找 Aicy 的网页入口。 */
public final class DexRoutes {
    public static final String WEB = "WebUrl";
    public static final String DEFAULT = "DefaultBrowserUrl";
    public static final String START = "ActivityJumpTool";
    public static final String SEARCH = "TextSearch";
    private static final String VIEW = "android.intent.action.VIEW";
    private static final String BROWSER = "com.android.browser.BrowserActivity";
    private static final String DEFAULT_BROWSER = "getDefaultBrowserPackageNameAsUser";
    private static final String START_FLAG = "disable_splash_ad";
    private static final String SEARCH_KEY = "tag_search_engine_url";
    private static final String CONTEXT = "Landroid/content/Context;";
    private static final String STRING = "Ljava/lang/String;";
    private static final int MAX_DEX = 64 * 1024 * 1024;
    private static final int FEATURE_VIEW = 1;
    private static final int FEATURE_BROWSER = 1 << 1;
    private static final int FEATURE_DEFAULT_BROWSER = 1 << 2;
    private static final int FEATURE_START_FLAG = 1 << 3;
    private static final int FEATURE_SEARCH_KEY = 1 << 4;

    public static final class Target {
        public final String kind;
        public final String className;
        public final String methodName;

        Target(String kind, String descriptor, String methodName) {
            this.kind = kind;
            this.className = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
            this.methodName = methodName;
        }
        @Override public String toString() { return kind + "=" + className + "." + methodName; }
    }
    private DexRoutes() {}

    public static List<Target> find(File... apks) throws IOException {
        List<Target> candidates = new ArrayList<>();
        for (File apk : apks) {
            try (ZipFile zip = new ZipFile(apk)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                    if (entry.getSize() > MAX_DEX) throw new IOException("DEX exceeds scan limit");
                    try (InputStream in = zip.getInputStream(entry)) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[16384];
                        int size;
                        while ((size = in.read(buffer)) != -1) {
                            if (out.size() + size > MAX_DEX) throw new IOException("DEX exceeds scan limit");
                            out.write(buffer, 0, size);
                        }
                        try { new Reader(out.toByteArray()).scan(candidates); }
                        catch (IndexOutOfBoundsException | IllegalArgumentException malformed) {
                            throw new IOException("Unsupported or malformed DEX", malformed);
                        }
                    }
                }
            }
        }
        return selectUniqueRoutes(candidates);
    }

    private static List<Target> selectUniqueRoutes(List<Target> candidates) {
        Map<String, List<Target>> groups = new LinkedHashMap<>();
        List<Target> searchListeners = new ArrayList<>();
        for (Target target : candidates) {
            if (SEARCH.equals(target.kind)) {
                searchListeners.add(target);
                continue;
            }
            List<Target> group = groups.get(target.className);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(target.className, group);
            }
            group.add(target);
        }

        // 三个入口须来自同一个工具类，且在所有 APK 中只能找到一组。
        List<Target> result = new ArrayList<>();
        int helpers = 0;
        for (List<Target> group : groups.values()) {
            int web = 0;
            int defaults = 0;
            int start = 0;
            for (Target target : group) {
                if (WEB.equals(target.kind)) web++;
                if (DEFAULT.equals(target.kind)) defaults++;
                if (START.equals(target.kind)) start++;
            }
            if (web == 1 && defaults == 1 && start == 1) {
                helpers++;
                result = group;
            }
        }
        if (helpers != 1) result = new ArrayList<>();
        if (searchListeners.size() == 1) result.add(searchListeners.get(0));
        return result;
    }

    private static final class Reader {
        final byte[] bytes;
        final ByteBuffer data;
        final int stringTable;
        final int typeTable;
        final int protoTable;
        final int methodTable;
        final String[] strings;
        final int[] stringFeatures;

        Reader(byte[] bytes) throws IOException {
            this.bytes = bytes;
            data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.length < 112 || bytes[0] != 'd' || bytes[1] != 'e' || bytes[2] != 'x'
                    || bytes[3] != '\n' || u32(36) != 112 || u32(40) != 0x12345678) {
                throw new IOException("Unsupported DEX header");
            }
            strings = new String[tableCount(56, 4, 60)];
            stringTable = u32(60);
            tableCount(64, 4, 68);
            typeTable = u32(68);
            tableCount(72, 12, 76);
            protoTable = u32(76);
            tableCount(88, 8, 92);
            methodTable = u32(92);
            tableCount(96, 32, 100);
            stringFeatures = new int[strings.length];
            for (int i = 0; i < strings.length; i++) {
                String value = readString(i);
                if (VIEW.equals(value)) stringFeatures[i] = FEATURE_VIEW;
                else if (BROWSER.equals(value)) stringFeatures[i] = FEATURE_BROWSER;
                else if (DEFAULT_BROWSER.equals(value)) stringFeatures[i] = FEATURE_DEFAULT_BROWSER;
                else if (START_FLAG.equals(value)) stringFeatures[i] = FEATURE_START_FLAG;
                else if (SEARCH_KEY.equals(value)) stringFeatures[i] = FEATURE_SEARCH_KEY;
            }
        }

        int tableCount(int at, int width, int offsetAt) {
            int count = u32(at);
            int offset = u32(offsetAt);
            if (count < 0 || offset < 0 || (long) offset + (long) count * width > bytes.length) {
                throw new IllegalArgumentException("Invalid DEX table");
            }
            return count;
        }

        int u16(int at) { return data.getShort(at) & 65535; }
        int u32(int at) { return data.getInt(at); }

        int readUleb128(int[] cursor) {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int next = bytes[cursor[0]++] & 255;
                value |= (next & 127) << shift;
                if ((next & 128) == 0 && value >= 0) return value;
            }
            throw new IllegalArgumentException("Invalid ULEB128");
        }
        String readString(int index) {
            if (strings[index] != null) return strings[index];
            int[] cursor = {u32(stringTable + index * 4)};
            readUleb128(cursor);
            int end = cursor[0];
            while (bytes[end] != 0) end++;
            return strings[index] = new String(bytes, cursor[0], end - cursor[0], StandardCharsets.UTF_8);
        }
        String readType(int index) { return readString(u32(typeTable + index * 4)); }

        boolean hasSignature(int method, String returns, String... params) {
            int proto = protoTable + u16(methodTable + method * 8 + 2) * 12;
            if (!returns.equals(readType(u32(proto + 4)))) return false;
            int list = u32(proto + 8);
            if (list == 0) return params.length == 0;
            if (u32(list) != params.length) return false;
            for (int i = 0; i < params.length; i++) {
                if (!params[i].equals(readType(u16(list + 4 + i * 2)))) return false;
            }
            return true;
        }

        void scan(List<Target> found) {
            for (int c = 0; c < u32(96); c++) {
                int classDef = u32(100) + c * 32;
                int classData = u32(classDef + 24);
                if (classData == 0) continue;
                int[] cursor = {classData};
                int fields = readUleb128(cursor) + readUleb128(cursor);
                int directMethods = readUleb128(cursor);
                int virtualMethods = readUleb128(cursor);
                for (int i = 0; i < fields; i++) {
                    readUleb128(cursor);
                    readUleb128(cursor);
                }
                scanMethods(cursor, directMethods, found);
                scanMethods(cursor, virtualMethods, found);
            }
        }

        void scanMethods(int[] cursor, int count, List<Target> found) {
            int methodIndex = 0;
            for (int i = 0; i < count; i++) {
                methodIndex += readUleb128(cursor);
                int flags = readUleb128(cursor);
                int code = readUleb128(cursor);
                if (code == 0) continue;
                boolean isStatic = (flags & 0x8) != 0;
                boolean web = isStatic && hasSignature(methodIndex, "V", CONTEXT, STRING);
                boolean start = isStatic && hasSignature(methodIndex, "Z", CONTEXT,
                        "Landroid/content/Intent;", "Landroid/os/Bundle;");
                boolean search = hasSignature(methodIndex, "V", "Landroid/view/View;");
                if (!web && !start && !search) continue;

                int features = readCodeFeatures(code);
                String kind = null;
                if (start && hasFeatures(features, FEATURE_START_FLAG)) kind = START;
                else if (web && hasFeatures(features, FEATURE_VIEW | FEATURE_DEFAULT_BROWSER)) kind = DEFAULT;
                else if (web && hasFeatures(features, FEATURE_VIEW | FEATURE_BROWSER)) kind = WEB;
                else if (search && hasFeatures(features, FEATURE_VIEW | FEATURE_BROWSER | FEATURE_SEARCH_KEY)) kind = SEARCH;
                if (kind != null) {
                    int methodOffset = methodTable + methodIndex * 8;
                    found.add(new Target(kind, readType(u16(methodOffset)), readString(u32(methodOffset + 4))));
                }
            }
        }

        int readCodeFeatures(int code) {
            int units = u32(code + 12);
            int at = code + 16;
            long limit = (long) at + (long) units * 2;
            if (units < 0 || limit > bytes.length) throw new IllegalArgumentException("Invalid code item");
            int result = 0;
            int end = (int) limit;
            while (at < end) {
                int instruction = u16(at);
                int op = instruction & 255;
                if (op == 0x1a || op == 0x1b) {
                    int index = op == 0x1a ? u16(at + 2) : u32(at + 2);
                    result |= stringFeatures[index];
                }

                // 指令长度以 16 位 code unit 计，三种 payload 的长度另行读取。
                int width;
                if (op == 0 && instruction != 0) {
                    int size = u16(at + 2);
                    long payload;
                    switch (instruction) {
                        case 0x100: payload = 4L + size * 2L; break;
                        case 0x200: payload = 2L + size * 4L; break;
                        case 0x300: payload = 4L + ((long) size * Integer.toUnsignedLong(u32(at + 4)) + 1) / 2; break;
                        default: throw new IllegalArgumentException("Invalid payload type");
                    }
                    if (payload < 0 || payload > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid payload");
                    width = (int) payload;
                } else {
                    width = WIDTHS[op];
                }
                if (width == 0 || (long) at + width * 2L > end) throw new IllegalArgumentException("Invalid instruction");
                at += width * 2;
            }
            return result;
        }
    }

    private static boolean hasFeatures(int actual, int required) {
        return (actual & required) == required;
    }

    private static final int[] WIDTHS = widths();
    private static int[] widths() {
        int[] widths = new int[256];
        Arrays.fill(widths, 1);
        for (int op : new int[] {0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29, 0xfe, 0xff}) widths[op] = 2;
        for (int op : new int[] {0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c, 0xfc, 0xfd}) widths[op] = 3;
        widths[0x18] = 5;
        widths[0xfa] = 4;
        widths[0xfb] = 4;
        Arrays.fill(widths, 0x2d, 0x3e, 2);
        Arrays.fill(widths, 0x44, 0x6e, 2);
        Arrays.fill(widths, 0x6e, 0x73, 3);
        Arrays.fill(widths, 0x74, 0x79, 3);
        Arrays.fill(widths, 0x90, 0xb0, 2);
        Arrays.fill(widths, 0xd0, 0xe3, 2);
        Arrays.fill(widths, 0xe3, 0xfa, 0);
        return widths;
    }
}
