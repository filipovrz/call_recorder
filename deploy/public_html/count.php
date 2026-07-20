<?php
/**
 * Public JSON stats for the landing page.
 * GET /count.php → {"count":N,"available":true|false}
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('Access-Control-Allow-Origin: *');

$apk = __DIR__ . '/downloads/evtinko-call-recorder.apk';
$counterFile = __DIR__ . '/data/downloads.json';

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

echo json_encode(
    [
        'count' => $count,
        'available' => is_file($apk),
        'version' => '0.1.0',
    ],
    JSON_UNESCAPED_UNICODE
);
