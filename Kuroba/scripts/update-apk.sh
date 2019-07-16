#!/usr/bin/env bash
git config --global user.name "Travis CI"
git config --global user.email "noreply+travis@fossasia.org"

git clone --quiet --branch=apk https://github.com/K1rakishou/Kuroba.git apk > /dev/null
cd apk
\cp -r ../*/**.apk .
\cp -r ../debug/output.json debug-output.json
\cp -r ../release/output.json release-output.json

git checkout --orphan temporary

git add --all .
git commit -am "[Auto] Update Test Apk ($(date +%Y-%m-%d.%H:%M:%S))"

git branch -D apk
git branch -m apk

git push origin apk --force --quiet > /dev/null