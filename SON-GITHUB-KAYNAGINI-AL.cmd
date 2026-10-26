@echo off
setlocal
set "BRANCH=build/apk668-verified-device-check"
set "SHA=5a0e01fc024cfcf6862531347f2bc23fca12ace9"
set "TARGET=%~dp0BUILD668-SON-GITHUB-KAYNAK"
where git >nul 2>nul
if errorlevel 1 (
  echo Git for Windows bulunamadi. https://git-scm.com/downloads/win adresinden kurun.
  echo Alternatif: GitHub Code - Download ZIP.
  pause
  exit /b 1
)
if exist "%TARGET%" (
  echo Hedef klasor zaten var; mevcut dosyalar korunuyor: "%TARGET%"
  pause
  exit /b 1
)
echo Son kaynak indiriliyor: %BRANCH%
git clone --single-branch --branch "%BRANCH%" "https://github.com/ferittasbek-png/BIST-VIOP-V6.2.2.git" "%TARGET%"
if errorlevel 1 (
  echo GitHub giris izinleri / internet baglantisi nedeniyle indirme basarisiz.
  pause
  exit /b 1
)
git -C "%TARGET%" rev-parse HEAD
if errorlevel 1 exit /b 1
rem Dokuman icin anlik son HEAD kullanilir; sabit 22 Ekim kaynak istenirse SHA ile karsilastirin.
echo.
echo SON GITHUB KAYNAGI INDIRILDI.
echo Android Studio icin "%TARGET%\android-v6323" klasorunu acin.
pause
