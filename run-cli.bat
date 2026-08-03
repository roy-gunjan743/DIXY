@echo off
cd /d D:\virtual_deadlock_detection
call gradlew.bat bootJar
docker compose up -d postgres redis
java "-Duser.timezone=UTC" "-Dspring.profiles.active=cli" "-Dspring.datasource.username=postgres" "-Dspring.datasource.password=gupi@1234" -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
pause