/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

/**
 * @type {number}
 */
let currentRealmIndex = Realm.current();

function resetRealm() {
    if (currentRealmIndex !== 0) Realm.dispose(currentRealmIndex);
    currentRealmIndex = Realm.createAllowCrossRealmAccess()
}

/**
 * @type {?Map<string, ?any>}
 */
let globalState = null;

function saveGlobalState() {
    globalState = new Map();
    const currentGlobal = Realm.global(currentRealmIndex)
    for (const k in currentGlobal) {
        globalState.set(k, currentGlobal[k]);
    }

    console.log(Array.from(globalState.entries()))
}

function restoreGlobalState() {
    if (globalState === null) throw Error("There is no saved state!")

    const currentGlobal = Realm.global(currentRealmIndex)

    console.log(Array.from(globalState.entries()))

    for (const k in currentGlobal) {
        let prev = globalState.get(k);
        if (prev !== currentGlobal[k]) {
            currentGlobal[k] = prev;
        }
    }
    globalState = null;
}

// To prevent accessing to current global state
resetRealm();

// noinspection InfiniteLoopJS
async function loop() {
    while (true) {
        let code = readline().replace(/\\n/g, '\n');

        try {
            switch (code) {
                case "!reset":
                    resetRealm()
                    break;
                case "!saveGlobalState":
                    saveGlobalState();
                    break;
                case "!restoreGlobalState":
                    restoreGlobalState();
                    break;
                default:
                    print(await Realm.eval(currentRealmIndex, code));
            }
        } catch(e) {
            printErr(e.stack != null ? e.stack : e.toString());
            printErr('\nCODE:\n' + code);
        }

        print('<END>');
    }
}

loop()
