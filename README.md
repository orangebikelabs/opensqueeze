# Open Squeeze

## Overview

Introducing Open Squeeze, an Open Source fork of the commercial Android
app [Orange Squeeze](https://orangebikelabs.com/products/orangesqueeze/) from Orange Bike Labs.
Orange Squeeze is an Android app that can be used to
control [Logitech Squeezebox](https://en.wikipedia.org/wiki/Squeezebox_(network_music_player))
devices on the [Logitech Media Server](https://en.wikipedia.org/wiki/Logitech_Media_Server)
platform.

Open Squeeze is a *very* bad example of how a modern Android app should be developed. It has a
decade of legacy code/cruft and has survived to this day despite the very schizophrenic state of
Android development. You've been warned.

## History of Orange|Open Squeeze

Orange Squeeze 1.0 was released on December 15, 2011 targeting a minimum of Android 2.1. Since then
the product has undergone development in fits and starts, but as of Spring 2020 most development
stopped. Open Squeeze was released in July 2022 with Orange Squeeze branding and certain other
unreleasable components removed, but is otherwise fully-functional.

## Releases
Find pre-built APK releases (alpha, beta, and release) in the project's [Github Releases](https://github.com/orangebikelabs/opensqueeze/releases).

You can also just get the [latest release here](https://github.com/orangebikelabs/opensqueeze/releases/latest).

## Building

Use Android Studio Dolphin (2021.3.1) release to build the project. You need to [install and configure](https://developer.android.com/about/versions/12/setup-sdk) the Android SDK first.

```
# .\gradlew assembleRelease
```

Then look in `app\build\outputs\apk\release` for the unsigned APK file that you can deploy to your
device.

## Contributing

If you'd like to submit a PR for a change, please feel
free. [Orange Bike Labs](https://orangebikelabs.com)/[Ben Sandee](https://github.com/bensandee) acts
as stewards for the project for the time being.

Please use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) for development
moving forward.

Please mirror the existing code style as best as possible and use the project code formats defined
in the Android Studio workspace. Certain legacy code style in the app is hideous and should be
avoided for new code (e.g. the "m" member variable prefix).

It's possible that contributions may be folded into future releases of Orange Squeeze. If so, the
authors will be recognized in the release notes.

## Languages and Translations

Open Squeeze was developed by a native American English speaker. The German and French translations were
initially machine generated and then heavily modified by users and contributors.

Translations to new languages and modifications to existing translations should be done via
[Weblate](https://hosted.weblate.org/projects/open-squeeze/) which is freely provided for
Open Source projects. Thank you to them for providing this service!

## License

The license for the code is [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

