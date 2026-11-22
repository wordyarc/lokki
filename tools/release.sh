#!/usr/bin/env bash
set -euo pipefail

die() {
    echo "${0##*/}: $*" >&2
    exit 1
}

replace() {
    local file=$1 expr=$2
    sed -E "$expr" "$file" >"$file.tmp" && mv "$file.tmp" "$file"
}

(( $# == 1 )) || { echo "usage: ${0##*/} <version>" >&2; exit 2; }

version=$1
tag=v$version
semver='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?$'

[[ $version =~ $semver ]] || die "$version is not MAJOR.MINOR.PATCH[-PRERELEASE]"
git diff --quiet HEAD || die "uncommitted changes"
git fetch --quiet origin master
git merge-base --is-ancestor HEAD origin/master || die "HEAD is not on origin/master"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "tag $tag already exists"

plugin=$(sed -n 's/^group=//p' gradle.properties)
replace gradle.properties "s/^version=.*/version=${version%%-*}-SNAPSHOT/"
[[ $version == *-* ]] || replace README.md "s/(id\(\"${plugin//./\\.}\"\) version )\"[^\"]*\"/\1\"$version\"/"

refs=("refs/tags/$tag")
if ! git diff --quiet HEAD; then
    if [[ $(git rev-parse HEAD) != $(git rev-parse origin/master) ]]; then
        git checkout -- gradle.properties README.md
        die "HEAD is behind origin/master, cannot commit the version"
    fi
    git commit --quiet -m "Release $version" gradle.properties README.md
    refs+=("HEAD:refs/heads/master")
fi
[[ $version == *-* ]] || grep -qF "id(\"$plugin\") version \"$version\"" README.md || die "README.md does not apply the plugin with a version"

git tag -a -m "$tag" "$tag"
if ! git push --atomic origin "${refs[@]}"; then
    git tag -d "$tag" >/dev/null
    (( ${#refs[@]} == 1 )) || git reset --quiet --keep HEAD~1
    exit 1
fi
