# Open Squeeze

## Overview

Introducing Open Squeeze, an Open Source fork of the commercial Android
app [Orange Squeeze](https://orangebikelabs.com/products/orangesqueeze/)
from Orange Bike Labs. Orange Squeeze is an Android app that
controls [Logitech Squeezebox](https://en.wikipedia.org/wiki/Squeezebox_(network_music_player))
devices on the [Logitech Media Server](https://en.wikipedia.org/wiki/Logitech_Media_Server)
platform.

Open Squeeze is a *very* bad example of how a modern Android app should be developed. It has a
decade of legacy code/cruft and has survived to this day despite the very schizophrenic state of
Android development. You've been warned.

## History of Orange Squeeze

Orange Squeeze 1.0 was released on December 15, 2011 targeting a minimum of Android 2.1. Since then
the product has undergone development in fits and starts, but as of Spring 2020 most development has
stopped. Open Squeeze was released with Orange Squeeze branding and certain other unreleasable
components removed, but is otherwise fully-functional.

## Contributing

If you'd like to submit a PR for a change, please feel
free. [Orange Bike Labs](https://orangebikelabs.com)/[Ben Sandee](https://github.com/bensandee) will
act as stewards for the repository for the time being.

Use Android Studio Dolphin (2021.3.1) beta releases to build the project.

Please mirror the existing style as best as possible and use the project code formats defined in the
workspace. Certain legacy code style in the app is hideous and should be avoided for new code (e.g.
the "m" member variable prefix).

It's possible that contributions may be folded into future releases of Orange Squeeze. If so, the
authors will be recognized in the release notes.

## License

The license for the code is [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html).

