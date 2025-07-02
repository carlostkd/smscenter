#!/usr/bin/env bash

set -euo pipefail

############################ 1. PROMPTS ################################
read -rp "How many recent RECEIVED SMS do you want to display? " N_IN
[[ "$N_IN" =~ ^[1-9][0-9]*$ ]] || { echo "✖  '$N_IN' is not a positive number."; exit 1; }

read -rp "Also include sent messages? (y/N) " INC_SENT
if [[ "$INC_SENT" =~ ^[Yy]$ ]]; then
  read -rp "How many recent SENT SMS do you want to display? " N_SENT
  [[ "$N_SENT" =~ ^[1-9][0-9]*$ ]] || { echo "✖  '$N_SENT' is not a positive number."; exit 1; }
else
  N_SENT=0
fi

read -rp "Export the messages to CSV? (y/N) " ANSWER
if [[ "$ANSWER" =~ ^[Yy]$ ]]; then
  read -rp "CSV output file (default: sms_export.csv): " CSVFILE
  CSVFILE=${CSVFILE:-sms_export.csv}
  echo '"type","id","address","smsc","date","body"' > "$CSVFILE"
  EXPORT=true
else
  EXPORT=false
fi

############################ 2. PERMISSIONS ############################
adb wait-for-device
if ! adb shell appops get --uid shell READ_SMS 2>/dev/null | grep -q allow; then
  echo "Granting READ_SMS to shell user temporarily…"
  adb shell cmd appops set --uid shell READ_SMS allow || {
    echo "❌  Could not grant READ_SMS. Need root or userdebug ROM."; exit 1; }
fi

############################ 3. HELPERS ################################
TZ='Europe/Zurich'
esc() { printf '%s' "$1" | sed 's/"/""/g'; }

content_query() {  # $1 = uri
  adb shell "content query \
      --uri $1 \
      --projection _id,address,service_center,date,body \
      --sort 'date DESC'"
}

aggregate_rows() { 
  local current=""
  while IFS= read -r line; do
    if [[ $line =~ ^Row:[[:space:]] ]]; then
      [[ -n $current ]] && printf '%s\n' "$current"
      current=${line#Row: }
    else
      current+=" ${line}"
    fi
  done
  [[ -n $current ]] && printf '%s\n' "$current"
}

print_and_export() { 
  local raw="$1" limit="$2" label="$3"
  mapfile -t rows < <(printf '%s\n' "$raw" | aggregate_rows)
  rows=( "${rows[@]:0:$limit}" )

  printf '== %s SMS (%d) ==\n' "$(tr a-z A-Z <<<"$label")" "${#rows[@]}"
  for row in "${rows[@]}"; do
    [[ -z $row ]] && continue
    id=$(   grep -o '_id=[^ ]*'            <<<"$row" | cut -d= -f2)
    addr=$( grep -o 'address=[^ ]*'        <<<"$row" | cut -d= -f2)
    smsc=$( grep -o 'service_center=[^ ]*' <<<"$row" | cut -d= -f2)
    epoch_ms=$(echo "$row" | grep -o 'date=[0-9]\+' | cut -d= -f2)
    body=${row#*body=}

    epoch_s=$(( epoch_ms / 1000 ))
    humandate=$(TZ=$TZ date -d "@$epoch_s" '+%Y-%m-%d %H:%M:%S')

    printf '%s\n' '----------------------------------------'
    printf 'ID:    %s\n' "$id"
    printf 'From:  %s\n' "$addr"
    printf 'SMSC:  %s\n' "${smsc:-<none>}"
    printf 'Date:  %s\n' "$humandate"
    printf 'Body:  %s\n' "$body"

    if [[ "$EXPORT" == true ]]; then
      printf '"%s","%s","%s","%s","%s","%s"\n' \
        "$(esc "$label")" "$(esc "$id")" "$(esc "$addr")" \
        "$(esc "$smsc")" "$(esc "$humandate")" "$(esc "$body")" \
        >> "$CSVFILE"
    fi
  done
  printf '%s\n' '----------------------------------------'
}

############################ 4. FETCH + OUTPUT #########################
echo; echo "Fetching messages …"

raw_in=$(content_query content://sms/inbox)
print_and_export "$raw_in" "$N_IN" "received"

if (( N_SENT > 0 )); then
  raw_sent=$(content_query content://sms/sent)
  print_and_export "$raw_sent" "$N_SENT" "sent"
fi

[[ "$EXPORT" == true ]] && echo "✅ Exported to $CSVFILE"
                                                          
