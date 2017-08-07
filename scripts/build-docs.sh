#!/bin/bash

set -euo pipefail

rev=$(git rev-parse HEAD)
remoteurl=$(git ls-remote --get-url origin)
repodir=gh-pages
tag=$(git tag --points-at HEAD)
name=$tag
if [[ -z $name ]]; then
    name=master
fi
target=$repodir/$name

git fetch
if [[ -z $(git branch -r --list origin/gh-pages) ]]; then
    (
    mkdir "$repodir"
    cd "$repodir"
    git init
    git remote add origin "${remoteurl}"
    git checkout -b gh-pages
    git commit --allow-empty -m "Init"
    git push -u origin gh-pages
    )
elif [[ ! -d "$repodir" ]]; then
    git clone --branch gh-pages "${remoteurl}" "$repodir"
else
    (
    cd "$repodir"
    git pull
    )
fi

rm -fr doc
lein codox

# replace docs for current version with new docs
rm -fr "$target"
cp -r doc "$target"

cd "$repodir"
git add --all
git commit -m "Build docs from ${rev}."
git push origin gh-pages
