# GLiJ Eclipse p2 update site

The Eclipse integration uses the same **headless Tycho Maven reactor** as the
working CodeQL example. It does **not** need a local Eclipse installation,
Eclipse executable, manual p2 publisher, or shell script.

## Build

From the **repository root**, with JDK 21+, Maven 3.9+, and network access for
Tycho/p2 target dependencies:

```sh
mvn -B -ntp clean install
mvn -B -ntp -f glij-eclipse-plugin/pom.xml clean verify
```

The output is:

```text
glij-eclipse-plugin/lu.kbra.glij.eclipse.repository/target/
├── repository/                        # unpacked p2 update site
│   ├── plugins/
│   ├── features/
│   └── content.jar / artifacts.jar / p2.index ...
└── glij-eclipse-update-site.zip  # Eclipse Install New Software → Archive
```

The `eclipse` profile adds a nested 3-part reactor:

- `lu.kbra.glij.eclipse`: OSGi plug-in (`eclipse-plugin`)
- `lu.kbra.glij.eclipse.feature`: installable feature (`eclipse-feature`)
- `lu.kbra.glij.eclipse.repository`: p2 repository and ZIP (`eclipse-repository`)

The Eclipse 2026-09 platform is resolved from its published p2 repository.
Tycho 4.0.13 compiles the plug-in and produces the ZIP in `package`.
The two Maven invocations are intentional: Tycho's OSGi dependency resolver
runs before the ordinary Maven (pom-first) compiler JARs are built, so they
must be installed locally in the first step. The plug-in then embeds the
`glij-api`, `glij-binding-spi`, `glij-compiler`, and
`glij-engine-adapter` JARs on its OSGi `Bundle-ClassPath`.

## Install

1. Open Eclipse 2026-09 or another compatible Eclipse with JDT.
2. Choose **Help → Install New Software… → Add… → Archive…**.
3. Select `glij-eclipse-update-site.zip`.
4. Install **GLiJ for Eclipse** and restart Eclipse.
5. Right-click your Java project and choose **Enable GLiJ**.

For remote installation, CI publishes the unpacked `repository/` contents to
the `pages` branch. The resulting GitHub Pages URL can be entered in
**Install New Software** instead of selecting a local ZIP.

## GitHub Actions

- `.github/workflows/tests.yml`: core tests and complete Tycho ZIP verification
  on pushes and pull requests.
- `.github/workflows/build-release.yml`: tag (`v*`) or manual trigger,
  creates a downloadable ZIP artifact and publishes the p2 site to `pages`.
- `.github/workflows/deploy-pages.yml`: optional SSH mirror of that site.

No CI step installs Eclipse or invokes `scripts/build-eclipse-update-site.sh`.

For editor behavior, limitations, and source associations, see
[`lu.kbra.glij.eclipse/README.md`](lu.kbra.glij.eclipse/README.md).
