# fabrics-pocket-aos

## 로컬 웹앱을 앱에서 띄우기

debug 빌드는 `http://localhost:3000`을 시작 화면으로 연다.

1. 웹앱 dev 서버를 개발 머신의 `localhost:3000`에서 실행한다.
   다른 주소를 띄우려면 `local.properties`(커밋 제외)에 `debug.startUrl`을 쓴다.
   예: `debug.startUrl=http://localhost:5173`
2. 에뮬레이터의 `localhost`를 개발 머신으로 연결한다.
   에뮬레이터를 재시작하면 다시 실행해야 한다.
   ```
   adb reverse tcp:3000 tcp:3000   # 시작 URL의 포트
   ```
3. debug 빌드를 설치한다.
   ```
   ./gradlew installPocketDebug
   ```

- `10.0.2.2`나 LAN IP는 쓰지 않는다. `localhost`가 아니면 secure context가 아니어서
  카메라 등 일부 웹 API가 비활성화된다.
- 평문 HTTP는 debug 빌드의 `localhost`·`127.0.0.1`에만 허용된다.
- safe area(`env(safe-area-inset-*)`)는 WebView 144 이상에서만 값이 들어온다.
  `adb shell dumpsys webviewupdate | grep "Current WebView"`로 버전을 확인한다.
- 브릿지는 시작 URL과 같은 origin(포트 포함)에만 주입된다. 다른 포트의 localhost
  페이지를 검증하려면 `debug.startUrl`을 그 주소로 바꾼다.
