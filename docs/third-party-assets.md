# Third-party assets and notices

The root [MIT license](../LICENSE) covers original project work. It does not
replace the separate licenses or terms of the materials and dependencies below.

## Material Symbols

The `ic_grid_view`, `ic_palette`, `ic_tag`, `ic_check_circle`, `ic_expand_more`,
`ic_arrow_forward` and `ic_apps` drawable paths are Material Symbols Rounded
from [Google Material Design Icons](https://github.com/google/material-design-icons/tree/27e9ef1dbeedc13d682fece4a58e1eda4cb0961a/symbols/web).
Revision: `27e9ef1dbeedc13d682fece4a58e1eda4cb0961a`. SVG paths were mechanically converted to Android vectors.
Copyright Google LLC. [Apache 2.0 license](material-icons-LICENSE.txt).

## Gradle wrapper

The Gradle startup scripts (`gradlew`, `gradlew.bat`) retain their original
copyright and Apache-2.0 headers. The bundled `gradle/wrapper/gradle-wrapper.jar`
contains its Apache-2.0 license in `META-INF/LICENSE`. Preserve those notices
when redistributing the wrapper. See the
[upstream Gradle license](https://github.com/gradle/gradle/blob/master/LICENSE).

## Downloaded dependencies

Libraries and SDKs downloaded during the build retain their upstream licenses
and service terms. The project's MIT license does not relicense them. Direct
dependencies and versions are listed in the
[version catalog](../gradle/libs.versions.toml).

In particular, the Google Mobile Ads and User Messaging Platform integrations
require their own SDK/service setup and review. Consult the
[ads/privacy and release workflows](android-readiness.md) before distributing
a build. This notice is not a complete license inventory for every transitive
dependency or a certification of a downstream release.
