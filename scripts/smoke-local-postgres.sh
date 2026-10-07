#!/usr/bin/env bash
set -euo pipefail

api_base="${SMARTATTEND_API_BASE:-http://127.0.0.1:2000}"
pg_bin="${SMARTATTEND_PG_BIN:-/opt/homebrew/opt/postgresql@17/bin}"
pg_host="${SMARTATTEND_PG_HOST:-127.0.0.1}"
pg_port="${SMARTATTEND_PG_PORT:-55432}"
pg_db="${SMARTATTEND_PG_DB:-smartattend_test}"
pg_user="${SMARTATTEND_PG_USER:-smartattend}"
run_id="$(date +%s)$$"
course_code="TST${run_id}"
lecturer_email="lecturer${run_id}@oauife.edu.ng"
student_email="student${run_id}@student.oauife.edu.ng"
student_matric="TST/${run_id}"
password='LocalTest123!'
tmp_dir="$(mktemp -d)"
trap 'rm -f "$tmp_dir/roster.csv" "$tmp_dir/report.pdf" "$tmp_dir/far.json" "$tmp_dir/gate.json"; rmdir "$tmp_dir"' EXIT

post_json() {
  curl --fail-with-body -sS -X POST "$api_base$1" -H 'Content-Type: application/json' "${@:3}" --data "$2"
}

auth_header() {
  printf 'Authorization: Bearer %s' "$1"
}

expect_face_gate() {
  status="$(curl -sS -o "$tmp_dir/gate.json" -w '%{http_code}' "$api_base$1" \
    -H "$(auth_header "$student_token")")"
  test "$status" = 403
  jq -e '.code == "FACE_ENROLLMENT_REQUIRED"' "$tmp_dir/gate.json" >/dev/null
}

verification_code() {
  "$pg_bin/psql" -h "$pg_host" -p "$pg_port" -U "$pg_user" -d "$pg_db" -At \
    -c "select email_verification_token from users where email = '$1'"
}

printf 'Registering test accounts...\n'
post_json /api/auth/register "$(jq -n --arg email "$lecturer_email" --arg password "$password" \
  '{fullName:"Dr Local Test",email:$email,username:$email,password:$password,role:"LECTURER"}')" >/dev/null
post_json /api/auth/register "$(jq -n --arg email "$student_email" --arg password "$password" --arg matric "$student_matric" \
  '{fullName:"Student Local Test",email:$email,username:$email,password:$password,role:"STUDENT",matricNo:$matric}')" >/dev/null

unverified_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$api_base/api/auth/login" \
  -H 'Content-Type: application/json' --data "$(jq -n --arg email "$student_email" --arg password "$password" \
  '{emailOrUsername:$email,password:$password}')")"
test "$unverified_status" = 401

for email in "$lecturer_email" "$student_email"; do
  code="$(verification_code "$email")"
  [[ "$code" =~ ^[0-9]{6}$ ]]
  post_json /api/auth/verify-email "$(jq -n --arg email "$email" --arg token "$code" \
    '{email:$email,token:$token}')" >/dev/null
done

lecturer_token="$(post_json /api/auth/login "$(jq -n --arg email "$lecturer_email" --arg password "$password" \
  '{emailOrUsername:$email,password:$password}')" | jq -r .token)"
student_token="$(post_json /api/auth/login "$(jq -n --arg email "$student_email" --arg password "$password" \
  '{emailOrUsername:$email,password:$password}')" | jq -r .token)"
test -n "$lecturer_token"
test -n "$student_token"
curl --fail-with-body -sS "$api_base/api/auth/me" -H "$(auth_header "$student_token")" \
  | jq -e --arg email "$student_email" '.email == $email and .emailVerified == true' >/dev/null

printf 'Creating course and confirming roster...\n'
course="$(post_json /api/courses "$(jq -n --arg code "$course_code" \
  '{courseCode:$code,title:"Local Integration",semester:"First semester 2026/27",room:"LT 204",schedule:"Monday 10:00",supportingLecturerEmails:[]}')" \
  -H "$(auth_header "$lecturer_token")")"
course_id="$(jq -r .id <<<"$course")"
test -n "$course_id"
printf 'name,matricNo,email\nStudent Local Test,%s,%s\nAbsent Local Test,TST/ABS%s,absent%s@student.oauife.edu.ng\n' \
  "$student_matric" "$student_email" "$run_id" "$run_id" >"$tmp_dir/roster.csv"
curl --fail-with-body -sS -X POST "$api_base/api/courses/$course_code/roster-upload" \
  -H "$(auth_header "$lecturer_token")" -F "file=@$tmp_dir/roster.csv;type=text/csv" >/dev/null
curl --fail-with-body -sS -X POST "$api_base/api/courses/$course_code/confirm-roster" \
  -H "$(auth_header "$lecturer_token")" | jq -e '.status == "ACTIVE" and .rosterCount == 2' >/dev/null

printf 'Checking face-enrollment gate...\n'
expect_face_gate /api/courses/mine
expect_face_gate /api/attendance/sessions/active
expect_face_gate /api/attendance/sessions/history
expect_face_gate /api/reports/sessions/1/pdf

descriptor="$(node -e 'const values = Array(128).fill(0); values[0] = 1; console.log(JSON.stringify(values))')"
post_json /api/auth/onboard-face "$(jq -n --arg embedding "$descriptor" '{facialEmbedding:$embedding}')" \
  -H "$(auth_header "$student_token")" | jq -e '.success == true' >/dev/null
curl --fail-with-body -sS "$api_base/api/auth/me" -H "$(auth_header "$student_token")" \
  | jq -e '.faceEnrolled == true' >/dev/null
curl --fail-with-body -sS "$api_base/api/courses/mine" -H "$(auth_header "$student_token")" \
  | jq -e --arg code "$course_code" 'any(.[]; .courseCode == $code and .rosterCount == 2 and (.roster | length) == 0)' >/dev/null
curl --fail-with-body -sS "$api_base/api/courses/$course_id" -H "$(auth_header "$student_token")" \
  | jq -e '(.roster | length) == 0' >/dev/null

printf 'Starting session and checking in...\n'
session="$(post_json /api/attendance/sessions "$(jq -n --arg code "$course_code" \
  '{courseCode:$code,latitude:7.37,longitude:3.94}')" -H "$(auth_header "$lecturer_token")")"
session_id="$(jq -r .id <<<"$session")"
session_code="$(jq -r .sessionCode <<<"$session")"
[[ "$session_code" =~ ^[A-Z0-9]{6}$ ]]
curl --fail-with-body -sS "$api_base/api/attendance/sessions/active" -H "$(auth_header "$student_token")" \
  | jq -e --argjson id "$session_id" 'any(.[]; .id == $id and .sessionCode == null and .latitude == null and (.rosterSnapshot | length) == 0)' >/dev/null

far_payload="$(jq -n --arg code "$session_code" --arg embedding "$descriptor" \
  '{code:$code,latitude:8.0,longitude:4.0,facialEmbedding:$embedding}')"
far_status="$(curl -sS -o "$tmp_dir/far.json" -w '%{http_code}' -X POST \
  "$api_base/api/attendance/sessions/$session_id/check-ins" -H 'Content-Type: application/json' \
  -H "$(auth_header "$student_token")" --data "$far_payload")"
test "$far_status" = 400
jq -e '.message | contains("outside")' "$tmp_dir/far.json" >/dev/null

checkin_payload="$(jq -n --arg code "$session_code" --arg embedding "$descriptor" \
  '{code:$code,latitude:7.37,longitude:3.94,facialEmbedding:$embedding}')"
post_json "/api/attendance/sessions/$session_id/check-ins" "$checkin_payload" \
  -H "$(auth_header "$student_token")" | jq -e '.status == "PRESENT"' >/dev/null
duplicate_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
  "$api_base/api/attendance/sessions/$session_id/check-ins" -H 'Content-Type: application/json' \
  -H "$(auth_header "$student_token")" --data "$checkin_payload")"
test "$duplicate_status" = 409

curl --fail-with-body -sS -X POST "$api_base/api/attendance/sessions/$session_id/close" \
  -H "$(auth_header "$lecturer_token")" | jq -e '.status == "CLOSED" and .presentCount == 1' >/dev/null
curl --fail-with-body -sS "$api_base/api/attendance/sessions/history" -H "$(auth_header "$student_token")" \
  | jq -e --argjson id "$session_id" 'any(.[]; .id == $id and .myStatus == "PRESENT" and .sessionCode == null)' >/dev/null

student_pdf_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  "$api_base/api/reports/sessions/$session_id/pdf" -H "$(auth_header "$student_token")")"
test "$student_pdf_status" = 403
curl --fail-with-body -sS "$api_base/api/reports/sessions/$session_id/pdf" \
  -H "$(auth_header "$lecturer_token")" -o "$tmp_dir/report.pdf"
pdftotext "$tmp_dir/report.pdf" - | rg -q 'PRESENT'
pdftotext "$tmp_dir/report.pdf" - | rg -q 'ABSENT'

record_count="$("$pg_bin/psql" -h "$pg_host" -p "$pg_port" -U "$pg_user" -d "$pg_db" -At \
  -c "select count(*) from attendance_records where session_id = $session_id and status = 'PRESENT'")"
test "$record_count" = 1
printf 'PASS: %s, session %s, PostgreSQL record persisted, PDF includes present and absent rows.\n' \
  "$course_code" "$session_id"
printf 'Disposable lecturer: %s\nDisposable student: %s\nPassword: %s\n' \
  "$lecturer_email" "$student_email" "$password"
