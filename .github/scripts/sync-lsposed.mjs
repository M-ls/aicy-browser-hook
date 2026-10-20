import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const SOURCE = 'M-ls/aicy-browser-hook';
const TARGET = 'Xposed-Modules-Repo/io.github.m_ls.aicybrowserhook';
const PACKAGE = 'io.github.m_ls.aicybrowserhook';
const CERTIFICATE = '6d12194bb7ae583fc0facdc7a1038a6139beb5e459aaeb9f33bd60dfb2042d61';

export function parseTag(tag) {
    const match = /^([1-9][0-9]*)-([0-9]+\.[0-9]+\.[0-9]+)$/.exec(tag ?? '');
    if (!match) throw new Error('Tag must use versionCode-versionName, for example 17-1.1.5.');
    return { tag, code: BigInt(match[1]), version: match[2], apkName: `AicyBrowserHook-${tag}.apk` };
}

export function checkModuleText(text) {
    if (!text.trim() || /legacy|local\.aicy\.browserhook|双包名|两个包名|切换包名|测试记录|验证记录|实机验证|\bD8\b/i.test(text)) {
        throw new Error('LSPosed text contains package migration notes or development records.');
    }
    return text.replace(/\r\n/g, '\n').trim() + '\n';
}

export function releaseNotes(body, version) {
    const lines = (body ?? '').replace(/\r\n/g, '\n').trim().split('\n');
    const heading = lines.shift();
    if (heading !== `# ${version} 发布说明`) throw new Error('Source release notes do not match the version.');
    // 安装和附件说明留在个人仓库，LSP 只接收前面的更新内容。
    const changes = lines.join('\n').split(/^##\s/m)[0].trim();
    if (!/^-\s+\S/m.test(changes)) throw new Error('Release notes must describe the changes.');
    return checkModuleText(`## ${version}\n\n${changes}`);
}

export function expectedHash(checksums, filename) {
    const matches = checksums.split(/\r?\n/)
        .map(line => /^([a-f0-9]{64})\s+\*?(.+)$/i.exec(line))
        .filter(match => match && match[2] === filename);
    if (matches.length !== 1) throw new Error(`Expected one checksum for ${filename}.`);
    return matches[0][1].toLowerCase();
}

function hash(bytes) { return createHash('sha256').update(bytes).digest('hex'); }

export function checkApk(badging, signature, release) {
    const match = /^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'/m.exec(badging);
    if (!match || match[1] !== PACKAGE || match[2] !== release.code.toString() || match[3] !== release.version) {
        throw new Error('APK package or version does not match the LSPosed release.');
    }
    const signers = /^Number of signers:\s*(\d+)\s*$/m.exec(signature)?.[1];
    const certificates = [...signature.matchAll(/^Signer #\d+ certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$/gim)]
        .map(match => match[1].toLowerCase());
    if (signers !== '1' || certificates.length !== 1 || certificates[0] !== CERTIFICATE) {
        throw new Error(`APK signing certificate mismatch: signers=${signers ?? 'not reported'}, SHA256=${certificates.join(',') || 'not reported'}.`);
    }
}

class GitHub {
    constructor(token) { this.token = token; }

    async request(method, route, { body, binary = false, optional = false, upload = false } = {}) {
        const host = upload ? 'https://uploads.github.com' : 'https://api.github.com';
        const headers = {
            Accept: binary ? 'application/octet-stream' : 'application/vnd.github+json',
            Authorization: `Bearer ${this.token}`,
            'X-GitHub-Api-Version': '2022-11-28',
            'User-Agent': 'aicy-browser-hook-release',
        };
        if (body !== undefined) headers['Content-Type'] = Buffer.isBuffer(body) ? 'application/vnd.android.package-archive' : 'application/json';
        const response = await fetch(host + route, {
            method, headers, signal: AbortSignal.timeout(60000),
            body: body === undefined ? undefined : Buffer.isBuffer(body) ? body : JSON.stringify(body),
        });
        if (optional && response.status === 404) return null;
        if (!response.ok) {
            throw new Error(`${method} ${route}: GitHub returned ${response.status}.`);
        }
        if (binary) return Buffer.from(await response.arrayBuffer());
        return response.status === 204 ? null : response.json();
    }
}

async function download(api, repo, asset) {
    if (!asset || asset.state !== 'uploaded') throw new Error('Release asset is missing or incomplete.');
    return api.request('GET', `/repos/${repo}/releases/assets/${asset.id}`, { binary: true });
}

export async function syncMetadata(api, readme, summary) {
    const repo = `/repos/${TARGET}`;
    const reference = await api.request('GET', `${repo}/git/ref/heads/main`);
    const parent = await api.request('GET', `${repo}/git/commits/${reference.object.sha}`);
    const tree = await api.request('GET', `${repo}/git/trees/${parent.tree.sha}`);
    const updates = [];
    for (const [path, text] of [['README.md', readme], ['SUMMARY', summary]]) {
        const content = Buffer.from(text);
        const blobHash = createHash('sha1').update(`blob ${content.length}\0`).update(content).digest('hex');
        if (tree.tree.some(entry => entry.path === path && entry.type === 'blob' && entry.sha === blobHash)) continue;
        const blob = await api.request('POST', `${repo}/git/blobs`, { body: { content: content.toString('base64'), encoding: 'base64' } });
        updates.push({ path, mode: '100644', type: 'blob', sha: blob.sha });
    }
    if (!updates.length) return reference.object.sha;
    const nextTree = await api.request('POST', `${repo}/git/trees`, { body: { base_tree: parent.tree.sha, tree: updates } });
    const commit = await api.request('POST', `${repo}/git/commits`, { body: {
        message: 'Update module description and compatibility', tree: nextTree.sha, parents: [reference.object.sha],
    } });
    await api.request('PATCH', `${repo}/git/refs/heads/main`, { body: { sha: commit.sha, force: false } });
    return commit.sha;
}

export async function syncRelease(api, release, apk, notes, targetCommit) {
    const repo = `/repos/${TARGET}`;
    let destination = await api.request('GET', `${repo}/releases/tags/${release.tag}`, { optional: true });
    if (destination) {
        if (destination.prerelease || destination.assets.some(asset => asset.name !== release.apkName)
                || destination.assets.length > 1) throw new Error('Destination release has unexpected assets or release type.');
        if (!destination.draft && destination.assets.length !== 1) throw new Error('Published LSPosed release has no APK.');
        if (destination.assets.length) {
            const existing = await download(api, TARGET, destination.assets[0]);
            if (hash(existing) !== hash(apk)) throw new Error('Destination APK differs; the existing asset was left unchanged.');
        }
    } else {
        destination = await api.request('POST', `${repo}/releases`, { body: {
            tag_name: release.tag, target_commitish: targetCommit, name: release.version,
            body: notes, draft: true, prerelease: false,
        } });
    }
    if (!destination.assets.length) {
        const uploaded = await api.request('POST', `${repo}/releases/${destination.id}/assets?name=${encodeURIComponent(release.apkName)}`,
            { body: apk, upload: true });
        if (uploaded.name !== release.apkName || uploaded.state !== 'uploaded') throw new Error('APK upload is incomplete; the release remains a draft.');
    }
    if (destination.draft || destination.name !== release.version || destination.body !== notes) {
        // APK 先上传到草稿，再公开发布，确保 LSP 抓取到的版本自带安装包。
        await api.request('PATCH', `${repo}/releases/${destination.id}`, { body: {
            name: release.version, body: notes, draft: false, prerelease: false, make_latest: 'true',
        } });
    }
    const published = await api.request('GET', `${repo}/releases/tags/${release.tag}`);
    if (published.draft || published.prerelease || published.tag_name !== release.tag
            || published.name !== release.version || published.assets.length !== 1
            || published.assets[0].name !== release.apkName || published.assets[0].state !== 'uploaded' || published.body !== notes) {
        throw new Error('LSPosed release verification failed.');
    }
    console.log(`LSPosed release ready: ${published.html_url}`);
}

function buildTools() {
    const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
    if (!sdk) throw new Error('Android SDK is required to check the APK.');
    // 与 APK 构建使用同一版工具，避免 runner 升级后改变校验输出格式。
    const tools = join(sdk, 'build-tools', '35.0.0');
    if (!existsSync(join(tools, 'aapt')) || !existsSync(join(tools, 'apksigner'))) {
        throw new Error('Android SDK Build Tools 35.0.0 are required to check the APK.');
    }
    return tools;
}

export async function main() {
    const release = parseTag(process.env.RELEASE_TAG);
    if (!process.env.LSPOSED_RELEASE_TOKEN) throw new Error('Configure the LSPOSED_RELEASE_TOKEN repository secret to enable LSPosed publishing.');
    if (!process.env.SOURCE_GH_TOKEN) throw new Error('Source repository token is missing.');
    const source = new GitHub(process.env.SOURCE_GH_TOKEN);
    const target = new GitHub(process.env.LSPOSED_RELEASE_TOKEN);
    const sourceRepo = `/repos/${SOURCE}`;
    const published = await source.request('GET', `${sourceRepo}/releases/tags/${release.tag}`);
    if (published.draft || published.prerelease || published.name !== release.version || published.tag_name !== release.tag) {
        throw new Error('Source release is not a matching stable release.');
    }
    const latestSource = await source.request('GET', `${sourceRepo}/releases/latest`);
    const latestTarget = await target.request('GET', `/repos/${TARGET}/releases/latest`, { optional: true });
    if (parseTag(latestSource.tag_name).code > release.code
            || (latestTarget && parseTag(latestTarget.tag_name).code > release.code)) {
        console.log('A newer version is already published; skipping this older version.');
        return;
    }
    const notes = releaseNotes(published.body, release.version);
    const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
    const readme = checkModuleText(readFileSync(join(root, 'distribution/lsposed/README.md'), 'utf8'));
    const summary = checkModuleText(readFileSync(join(root, 'SUMMARY'), 'utf8'));
    const apks = published.assets.filter(asset => asset.name === release.apkName);
    const checksumAssets = published.assets.filter(asset => asset.name === 'SHA256SUMS.txt');
    if (apks.length !== 1 || checksumAssets.length !== 1) throw new Error('Source release must contain one registered APK and checksum file.');
    const apk = await download(source, SOURCE, apks[0]);
    const checksums = await download(source, SOURCE, checksumAssets[0]);
    if (hash(apk) !== expectedHash(checksums.toString('utf8'), release.apkName)) throw new Error('Source APK checksum mismatch.');
    const directory = mkdtempSync(join(process.env.RUNNER_TEMP || tmpdir(), 'aicy-lsposed-'));
    const apkPath = join(directory, release.apkName);
    writeFileSync(apkPath, apk);
    const tools = buildTools();
    const badging = execFileSync(join(tools, 'aapt'), ['dump', 'badging', apkPath], { encoding: 'utf8' });
    const signature = execFileSync(join(tools, 'apksigner'), ['verify', '--verbose', '--print-certs', apkPath], { encoding: 'utf8' });
    checkApk(badging, signature, release);
    const targetCommit = await syncMetadata(target, readme, summary);
    await syncRelease(target, release, apk, notes, targetCommit);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
