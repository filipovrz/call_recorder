<?php
/**
 * Serves the APK and increments the download counter.
 * Use this URL for the download button — not the raw .apk path.
 */
declare(strict_types=1);

header('X-Content-Type-Options: nosniff');

$apk = __DIR__ . '/downloads/evtinko-call-recorder.apk';
$dataDir = __DIR__ . '/data';
$counterFile = $dataDir . '/downloads.json';

if (!is_file($apk)) {
    http_response_code(404);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'APK файлът още не е качен.';
    exit;
}

if (!is_dir($dataDir)) {
    @mkdir($dataDir, 0755, true);
}

$count = 0;
$fp = @fopen($counterFile, 'c+');
if ($fp !== false) {
    if (flock($fp, LOCK_EX)) {
        $raw = stream_get_contents($fp);
        $data = [];
        if (is_string($raw) && $raw !== '') {
            $decoded = json_decode($raw, true);
            if (is_array($decoded)) {
                $data = $decoded;
            }
        }
        $count = isset($data['count']) ? (int) $data['count'] : 0;
        $count++;
        $payload = json_encode(
            [
                'count' => $count,
                'updated_at' => gmdate('c'),
            ],
            JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT
        );
        ftruncate($fp, 0);
        rewind($fp);
        fwrite($fp, $payload !== false ? $payload : '{"count":' . $count . '}');
        fflush($fp);
        flock($fp, LOCK_UN);
    }
    fclose($fp);
}

$filename = 'evtinko-call-recorder.apk';
$size = filesize($apk);

header('Content-Type: application/vnd.android.package-archive');
header('Content-Disposition: attachment; filename="' . $filename . '"');
header('Content-Length: ' . (string) $size);
header('Cache-Control: no-store');
header('X-Download-Count: ' . (string) $count);

readfile($apk);
exit;
