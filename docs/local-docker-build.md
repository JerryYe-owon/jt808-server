# Local Docker builds

From the parent JT-Gateway directory, `./build_images.sh 808` builds and deploys
JT808, passing `--build-arg LOCAL_MAVEN_REPO=true`. The script
retains its existing image export and remote deployment steps.

No environment variable or extra argument is needed when running the script.
The Aliyun mirror settings are embedded in the Dockerfile. CI does not use this
script; `LOCAL_MAVEN_REPO` defaults to `false`, so Maven uses its defaults unless
CI supplies its own settings secret.

To build locally without deploying, run from the jt808-server repository root:

```sh
docker build --build-arg LOCAL_MAVEN_REPO=true -t llp/jt808-server:latest .
```

The local settings mirror Central through Aliyun for dependencies and
plugins. Other repositories are unchanged. Mirror failures do not automatically
fall back to Central; omit the local build flag to use defaults. Only the existing
`stage` Maven profile is activated.

BuildKit caches `/root/.m2/repository` between builds. Settings remain outside the
cache mount. Local settings are generated inside the builder stage; supplied
settings secrets are mounted only during the Maven build.
The narrower cache path may require downloading dependencies again on the first
build after this change.

CI can choose its repositories by supplying its own Maven settings file:

```sh
docker build --secret id=maven_settings,src=/path/to/ci-settings.xml -t jt808-server .
```

A supplied settings secret takes precedence over the local mirror flag.

Host Maven settings are not automatically inherited by Docker builds. BuildKit
does not invalidate cached build steps when secret contents change; use
`--no-cache-filter jar-builder` when a changed settings file must take effect.
