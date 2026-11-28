$root = "C:\Users\manoj.gowda\Documents\Personal project"
$envFile = Join-Path $root 'docker\postgres\.env.local'
$eduAppPassword = (Get-Content $envFile | Select-String '^EDU_APP_PASSWORD=(.*)$').Matches[0].Groups[1].Value
$eduPlatformPassword = (Get-Content $envFile | Select-String '^EDU_PLATFORM_PASSWORD=(.*)$').Matches[0].Groups[1].Value
$backendLog = Join-Path $root 'logs\backend.log'

$env:SPRING_DATASOURCE_USERNAME = 'edu_app'
$env:SPRING_DATASOURCE_PASSWORD = $eduAppPassword
$env:PLATFORM_ADMIN_DATASOURCE_PASSWORD = $eduPlatformPassword
$env:SPRING_PROFILES_ACTIVE = 'dev'

Set-Location (Join-Path $root 'api')
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev' 2>&1 | Tee-Object -FilePath $backendLog
