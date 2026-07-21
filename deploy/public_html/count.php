<?php
/**
 * Public JSON stats for the landing page.
 * GET /count.php → {"count":N,"available":true|false,"version":"..."}
 *
 * Version comes from version.json (updated when a new APK is prepared for upload).
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
if (is_file($versionFile)) {
    $vraw = @file_get_contents($versionFile);
    if (is_string($vraw) && $vraw !== '') {
        $vdecoded = json_decode($vraw, true);
        if (is_array($vdecoded)) {
            if (isset($vdecoded['version']) && is_string($vdecoded['version'])) {
                $version = $vdecoded['version'];
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
    ],
    JSON_UNESCAPED_UNICODE
);
