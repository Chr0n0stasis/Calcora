import pathlib
path = pathlib.Path(r"D:\Users\Chr0n0s\Documents\Gits\Calcora\Calcora\.github\workflows\ios.yml")
content = path.read_text('utf8')

sim_block = """      - name: Build simulator validation app
        shell: bash
        run: |
          set -euo pipefail
          xcodebuild -project iosApp/CalcoraIOS.xcodeproj -scheme CalcoraIOS -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO SYMROOT="$RUNNER_TEMP/sim-build" OBJROOT="$RUNNER_TEMP/sim-obj" build
          SIM_APP="$(find "$RUNNER_TEMP/sim-build" -path '*/Debug-iphonesimulator/CalcoraIOS.app' -type d -print -quit)"
          test -n "$SIM_APP"
          test -f "$SIM_APP/aide_cas"
          test -f "$SIM_APP/zh/aide_cas"
          file "$SIM_APP/CalcoraIOS"
"""

content = content.replace(sim_block, "")
path.write_text(content, 'utf8')
