#!/usr/bin/env bash



set -euo pipefail

############# 0. Prerequisites ##################################################
if ! command -v zenity >/dev/null;   then echo "Install zenity";   exit 1; fi
if ! command -v adb    >/dev/null;   then zenity --error --text="adb not found"; exit 1; fi

############# 1. Zenity PROMPTS #################################################
N_IN=$(zenity --entry \
        --title="SMS Viewer" \
        --text="How many recent RECEIVED SMS?" \
        --entry-text="5") || exit 1
[[ "$N_IN" =~ ^[1-9][0-9]*$ ]] || { zenity --error --text="Invalid number"; exit 1; }

zenity --question --title="Include Sent SMS?" \
       --text="Include SENT messages as well?"
INCLUDE_SENT=$?
if [[ $INCLUDE_SENT -eq 0 ]]; then
  N_SENT=$(zenity --entry \
            --title="Sent Messages" \
            --text="How many recent SENT SMS?" \
            --entry-text="1") || exit 1
  [[ "$N_SENT" =~ ^[1-9][0-9]*$ ]] || { zenity --error --text="Invalid number"; exit 1; }
else
  N_SENT=0
fi

zenity --question --title="Export to CSV?" \
       --text="Export the messages to a CSV file?"
DO_EXPORT=$?
if [[ $DO_EXPORT -eq 0 ]]; then
  CSVFILE=$(zenity --file-selection --save --confirm-overwrite \
            --title="Choose CSV Output File" \
            --filename="$HOME/sms_export.csv") || exit 1
  EXPORT=true
else
  EXPORT=false; CSVFILE=""
fi

############# 2. Permission check ###############################################
adb wait-for-device
if ! adb shell appops get --uid shell READ_SMS 2>/dev/null | grep -q allow; then
  adb shell cmd appops set --uid shell READ_SMS allow 2>/dev/null || {
    zenity --error --text="Cannot grant READ_SMS (need root/userdebug)"; exit 1; }
fi

############# 3. Helper ###############################################
TZ='Europe/Zurich'
esc() { printf '%s' "$1" | sed 's/"/""/g'; }

content_query () {            # $1 uri
  adb shell "content query \
      --uri $1 \
      --projection _id,address,service_center,date,body \
      --sort 'date DESC'"
}

aggregate_rows () {           # read stdin → one row per line
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

process_rows () {             # $1 raw rows, $2 limit, $3 label
  local raw="$1" limit="$2" label="$3"
  mapfile -t rows < <(printf '%s\n' "$raw" | aggregate_rows)
  rows=( "${rows[@]:0:$limit}" )

  printf '\n== %s SMS (%d) ==\n' "$(tr a-z A-Z <<<"$label")" "${#rows[@]}"
  for row in "${rows[@]}"; do
    [[ -z $row ]] && continue
    id=$(grep -o '_id=[^ ]*' <<<"$row" | cut -d= -f2)
    addr=$(grep -o 'address=[^ ]*' <<<"$row" | cut -d= -f2)
    smsc=$(grep -o 'service_center=[^ ]*' <<<"$row" | cut -d= -f2)
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

############# 4. CSV  ########################################
if [[ "$EXPORT" == true ]]; then
  echo '"type","id","address","smsc","date","body"' > "$CSVFILE"
fi

############# 5. Fetch and display ################################################
echo "Fetching messages …"
raw_in=$(content_query content://sms/inbox)
process_rows "$raw_in" "$N_IN" "received"

if (( N_SENT > 0 )); then
  raw_sent=$(content_query content://sms/sent)
  process_rows "$raw_sent" "$N_SENT" "sent"
fi

############# 6. done #########################################################
if [[ "$EXPORT" == true ]]; then
  zenity --info --text="CSV exported to: $CSVFILE"
fi
echo "Done."
