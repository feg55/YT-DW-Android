# Contributing

1. Create a separate branch for your change.
2. Do not commit an SDK, keystore, `local.properties`, or build output.
3. Before opening a pull request, run:

```bash
./gradlew check assembleDebug
```

In the pull request description, state the scenario you tested and the Android version used. For downloader fixes, attach a sanitized technical log without cookies, tokens, or personal URLs.
