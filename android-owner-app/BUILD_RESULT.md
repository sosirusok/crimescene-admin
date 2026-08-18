# 크라임씬플레이 예약 알림 앱 최종 빌드 결과

- 소스 확인: success
- 서버 확인: success
- Android SDK: success
- 서명키 생성: success
- APK 빌드: success
- APK 검증: failure
- 전달 파일 준비: skipped
- 앱 버전: 1.1.1
- 패키지: com.crimesceneplay.owner
- 데이터 연결: Supabase PostgreSQL 예약 알림 큐
- 수신 방식: 20초 유지 연결을 반복하는 실시간 long-poll
- GitHub Actions 실행 번호: 32114933098
- 산출물 이름: Crimescene-Owner-App-final
- 실행 시각: 2026-08-18T08:10:54Z

결과: 실패

## 확인 로그
```text
--- api.log ---
{"ok":true,"service":"crimescene-owner-app","version":"1.1.1","realtime":"LONG_POLL"}{"error":"앱 연결이 만료되었습니다. 다시 연결해 주세요."}--- sdk.log ---
yes: standard output: Broken pipe
Loading package information...                                                  Loading local repository...                                                     [                                       ] 3% Loading local repository...        [                                       ] 3% Fetch remote repository...         [=                                      ] 3% Fetch remote repository...         [=                                      ] 4% Fetch remote repository...         [=                                      ] 5% Fetch remote repository...         [==                                     ] 5% Fetch remote repository...         [==                                     ] 6% Fetch remote repository...         [==                                     ] 7% Fetch remote repository...         [==                                     ] 7% Computing updates...               [===                                    ] 8% Computing updates...               [===                                    ] 10% Computing updates...              [=======================================] 100% Computing updates...             

--- signing.log ---
Generating 4,096 bit RSA key pair and self-signed certificate (SHA384withRSA) with a validity of 10,000 days
	for: CN=Crimescene Play Owner App, OU=Seomyeon Branch, O=Crimescene Play, L=Busan, C=KR
[Storing /tmp/Crimescene-Owner-Update-Key.jks]
--- build.log ---
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.7/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :app:clean UP-TO-DATE
> Task :app:preBuild UP-TO-DATE
> Task :app:preReleaseBuild UP-TO-DATE
> Task :app:javaPreCompileRelease
> Task :app:checkReleaseAarMetadata
> Task :app:generateReleaseResValues
> Task :app:mapReleaseSourceSetPaths
> Task :app:generateReleaseResources
> Task :app:packageReleaseResources
> Task :app:mergeReleaseResources
> Task :app:createReleaseCompatibleScreenManifests
> Task :app:extractDeepLinksRelease
> Task :app:parseReleaseLocalResources
> Task :app:processReleaseMainManifest
> Task :app:processReleaseManifest
> Task :app:extractProguardFiles
> Task :app:mergeReleaseJniLibFolders
> Task :app:mergeReleaseNativeLibs NO-SOURCE
> Task :app:stripReleaseDebugSymbols NO-SOURCE
> Task :app:extractReleaseNativeSymbolTables NO-SOURCE
> Task :app:mergeReleaseNativeDebugMetadata NO-SOURCE
> Task :app:checkReleaseDuplicateClasses
> Task :app:desugarReleaseFileDependencies
> Task :app:mergeReleaseStartupProfile
> Task :app:mergeReleaseArtProfile
> Task :app:mergeExtDexRelease
> Task :app:mergeReleaseShaders
> Task :app:compileReleaseShaders NO-SOURCE
> Task :app:generateReleaseAssets UP-TO-DATE
> Task :app:mergeReleaseAssets
> Task :app:processReleaseManifestForPackage
> Task :app:compressReleaseAssets
> Task :app:extractReleaseVersionControlInfo
> Task :app:processReleaseJavaRes NO-SOURCE
> Task :app:collectReleaseDependencies
> Task :app:mergeReleaseJavaResource
> Task :app:processReleaseResources
> Task :app:sdkReleaseDependencyData

> Task :app:compileReleaseJavaWithJavac
Note: /home/runner/work/crimescene-admin/crimescene-admin/android-owner-app/app/src/main/java/com/crimesceneplay/owner/NotificationSyncService.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.

> Task :app:generateReleaseLintVitalReportModel
> Task :app:dexBuilderRelease
> Task :app:mergeReleaseGlobalSynthetics
> Task :app:validateSigningRelease
> Task :app:writeReleaseAppMetadata
> Task :app:writeReleaseSigningConfigVersions
> Task :app:optimizeReleaseResources
> Task :app:mergeDexRelease
> Task :app:compileReleaseArtProfile
> Task :app:packageRelease
> Task :app:createReleaseApkListingFileRedirect
> Task :app:lintVitalAnalyzeRelease
> Task :app:lintVitalReportRelease
> Task :app:lintVitalRelease
> Task :app:assembleRelease
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/assemble-1787040621575.json

BUILD SUCCESSFUL in 37s
44 actionable tasks: 43 executed, 1 up-to-date
--- verify.log ---
/home/runner/work/_temp/7a2b2488-fdfd-4203-80a6-e44c8e62f30c.sh: line 4: apksigner: command not found
```
