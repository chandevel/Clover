#!/usr/bin/env bash
git config --global user.name "Travis CI"
git config --global user.email "noreply+travis@fossasia.org"

git clone --quiet --branch=apk https://fossasia:$GITHUB_API_KEY@github.com/fossasia/open-event-android apk > /dev/null
cd apk
\cp -r ../app/build/outputs/apk/*/**.apk .
\cp -r ../app/build/outputs/apk/debug/output.json debug-output.json
\cp -r ../app/build/outputs/apk/release/output.json release-output.json

git checkout --orphan temporary

git add --all .
git commit -am "[Auto] Update Test Apk ($(date +%Y-%m-%d.%H:%M:%S))"

git branch -D apk
git branch -m apk

git push origin apk --force --quiet > /dev/null