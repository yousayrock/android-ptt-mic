# Car Noise Reduction

## Modes

| Mode | Processing | Intended use |
| --- | --- | --- |
| OFF | Gain and hard PTT gate only | Baseline latency and artifact measurement |
| STANDARD | Android `NoiseSuppressor` when available, conservative HPF, optional limiter | General indoor/road use |
| CAR | HPF tuned for engine/road rumble, conservative RNNoise or NS, light compressor/limiter, optional AGC | Vehicle cabin |

AEC is experimental and only enabled when a speaker reference is available. It should not be enabled merely because the device reports AEC support.

## Comparison plan

Measure the same speech script and noise route for each chain:

1. OFF
2. Android NoiseSuppressor only
3. RNNoise only
4. HPF + compressor
5. STANDARD
6. CAR
7. AEC variants when speaker playback is present

Record end-to-end latency, CPU load, battery impact, dropouts, speech intelligibility, noise attenuation, pumping, musical noise, clipping, and false suppression. Keep processing order and parameters in the test record so results are reproducible.

## Low-latency rule

PTT usability wins over maximum noise reduction. Do not stack processors whose combined buffering or look-ahead materially increases press/release latency. Each mode must have a bypass and its measured latency must be visible in diagnostics.
