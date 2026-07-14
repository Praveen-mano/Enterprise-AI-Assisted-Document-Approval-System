@echo off
setlocal
echo Checking Docker PostgreSQL connection...
docker compose exec postgres psql -U approval -d approvaldb -c "select current_database(), current_user, now();"
echo.
echo Checking seeded users table if it exists...
docker compose exec postgres psql -U approval -d approvaldb -c "select email, role_name, active from app_users order by email;" 2>nul
