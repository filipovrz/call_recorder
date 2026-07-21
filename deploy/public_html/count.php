<?php
/**
 * Public JSON stats for the landing page.
 * Version is read automatically from inside the APK (assets/app_version.txt).
 * Optional fallback: version.json in public_html.
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('Access-Control-Allow-Origin: *');

$apk = __DIR__ . '/downloads/evtinko-call-recorder.apk';
$counterFile = __DIR__ . '/data/downloads.json';
$versionFile = __DIR__ . '/version.json';

$count = 0;
if (is_file($counterFile)) {
    $raw = @file_get_contents($counterFile);
    if (is_string($raw) && $raw !== '') {
        $decoded = json_decode($raw, true);
        if (is_array($decoded) && isset($decoded['count'])) {
            $count = (int) $decoded['count'];
        }
    }
}

$version = '0.0.0';
$minAndroid = '8.0';
$versionSource = 'none';

$fromApk = read_version_from_apk($apk);
if ($fromApk !== null) {
    $version = $fromApk['version'];
    if (isset($fromApk['minAndroid'])) {
        $minAndroid = $fromApk['minAndroid'];
    }
    $versionSource = 'apk';
} elseif (is_file($versionFile)) {
    $vraw = @file_get_contents($versionFile);
    if (is_string($vraw) && $vraw !== '') {
        $vdecoded = json_decode($vraw, true);
        if (is_array($vdecoded)) {
            if (isset($vdecoded['version']) && is_string($vdecoded['version'])) {
                $version = $vdecoded['version'];
                $versionSource = 'json';
            }
            if (isset($vdecoded['minAndroid']) && is_string($vdecoded['minAndroid'])) {
                $minAndroid = $vdecoded['minAndroid'];
            }
        }
    }
}

echo json_encode(
    [
        'count' => $count,
        'available' => is_file($apk),
        'version' => $version,
        'minAndroid' => $minAndroid,
        'versionSource' => $versionSource,
    ],
    JSON_UNESCAPED_UNICODE
);

/**
 * @return array{version:string,minAndroid?:string}|null
 */
function read_version_from_apk(string $apkPath): ?array
{
    if (!is_file($apkPath) || !class_exists('ZipArchive')) {
        return null;
    }
    $zip = new ZipArchive();
    if ($zip->open($apkPath) !== true) {
        return null;
    }
    // Prefer plain text asset written at build time.
    $candidates = [
        'assets/app_version.txt',
        'assets/version.txt',
    ];
    $raw = false;
    foreach ($candidates as $name) {
        $raw = $zip->getFromName($name);
        if ($raw !== false && trim((string) $raw) !== '') {
            break;
        }
    }
    $zip->close();
    if ($raw === false) {
        return null;
    }
    $lines = preg_split('/\R/', trim((string) $raw)) ?: [];
    $version = trim((string) ($lines[0] ?? ''));
    if ($version === '') {
        return null;
    }
    $out = ['version' => $version];
    foreach ($lines as $line) {
        if (stripos($line, 'minAndroid=') === 0) {
            $out['minAndroid'] = trim(substr($line, strlen('minAndroid=')));
        }
    }
    return $out;
}
