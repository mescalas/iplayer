#!/usr/bin/env bash
# Drives the app with remote-control key events on an Android TV emulator and captures screenshots.
set -u
OUT=${OUT:-shots}
mkdir -p "$OUT"
n=0
k() { for c in "$@"; do adb shell input keyevent "$c"; sleep 0.6; done; }
shot() { n=$((n+1)); f=$(printf "%s/%02d_%s.png" "$OUT" "$n" "$1"); adb exec-out screencap -p > "$f"; echo "shot $f"; }
txt() { adb shell input text "$1"; sleep 0.8; }
UP=19; DOWN=20; LEFT=21; RIGHT=22; OK=23; BACK=4; ENTER=66; MENU=82

adb shell wm size 1920x1080
adb shell wm density 320
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb logcat -c
adb shell settings put secure show_ime_with_hard_keyboard 0
adb install -r app.apk
adb shell am start -n com.iplayer.tv/.MainActivity
sleep 8; shot welcome
k $OK; sleep 4; shot add_account
# The software emulator is slow: wait for each input dialog before typing, and for it to close.
field() { k $OK; sleep 5; txt "$1"; sleep 2; shot "dialog_$2"; k $ENTER; sleep 4; }
k $DOWN $DOWN; sleep 1; field "http://10.0.2.2:8000" server
k $DOWN; sleep 1; field "demo" user
k $DOWN; sleep 1; field "demo" pass
shot form_filled
k $DOWN $DOWN $DOWN; sleep 1; k $OK; sleep 3; shot connecting
sleep 20; shot home
k $DOWN; sleep 2; shot home_focus
k $DOWN $DOWN; sleep 2; shot home_movies_row
k $DOWN $DOWN; sleep 2; shot home_lower_rows
k $UP $UP $UP $UP $UP; sleep 2; shot home_back_to_top
# Live TV tab
k $BACK; sleep 1; k $RIGHT; sleep 2; shot live_tab
k $DOWN; sleep 1; k $RIGHT; sleep 2; shot live_channels
k $DOWN $DOWN; sleep 2; shot live_channel_focus
k $OK; sleep 7; shot player_live
k $DOWN; sleep 5; shot player_zap
k $LEFT; sleep 2; shot player_channel_list
k $BACK; sleep 1; k $RIGHT; sleep 2; shot player_options
k $BACK; sleep 1; k $BACK; sleep 1; k $BACK; sleep 3; shot live_after_player_preview
k $RIGHT; sleep 2; shot live_guide_catchup
# Movies
k $BACK; sleep 1; k $RIGHT; sleep 3; shot movies_tab
k $DOWN; sleep 1; k $RIGHT; sleep 2; shot movies_grid_focus
k $OK; sleep 4; shot movie_detail
k $OK; sleep 8; shot movie_player
k $OK; sleep 2; shot movie_paused
k $RIGHT $RIGHT $RIGHT; sleep 0.3; shot movie_seek
sleep 2; k $BACK; sleep 1; k $BACK; sleep 3; shot movie_detail_after
k $BACK; sleep 3; shot movies_after_back
# Series
k $BACK; sleep 1; k $RIGHT; sleep 3; shot series_tab
k $DOWN; sleep 1; k $RIGHT; sleep 1; k $OK; sleep 4; shot series_detail
k $DOWN; sleep 2; shot series_season_tabs
k $RIGHT; sleep 2; shot series_season_2
k $LEFT; sleep 2; shot series_season_1
k $DOWN; sleep 2; shot series_episodes
k $RIGHT $RIGHT; sleep 2; shot series_episode_focus
k $BACK; sleep 2
# Search
k $BACK; sleep 1; k $RIGHT; sleep 3; shot search_tab
k $DOWN; sleep 1; k $OK $RIGHT $RIGHT $OK; sleep 3; shot search_results
# Settings
k $BACK; sleep 1; k $RIGHT; sleep 3; shot settings
k $DOWN $DOWN; sleep 2; shot settings_playback
k $BACK; sleep 1; k $LEFT $LEFT $LEFT $LEFT $LEFT; sleep 3; shot home_final

echo "==== crashes / errors ===="
adb logcat -d | grep -E "AndroidRuntime|FATAL|Exception|com.iplayer" | grep -vE "ResourcesCompat|chatty" | head -80
