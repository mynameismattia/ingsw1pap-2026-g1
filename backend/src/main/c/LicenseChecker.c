#include "ch_supsi_dti_backend_license_LicenseChecker.h"
#include <stdbool.h>
#include <string.h>
#include <ctype.h>

static bool validate(const char *key) {
    // 1. Format check: XXXXX-XXXXX-XXXXX-XXXXX (23 caratteri totali)
    if (strlen(key) != 23) return false;
    for (int i = 0; i < 23; i++) {
        if (i == 5 || i == 11 || i == 17) {
            if (key[i] != '-') return false;
        }
    }

    // 2. Conta le occorrenze di ogni lettera (case-insensitive)
    int counts[26] = {0};
    for (int i = 0; i < 23; i++) {
        unsigned char c = (unsigned char)key[i];
        if (c == '-') continue;
        if (isalpha(c)) {
            counts[tolower(c) - 'a']++;
        }
    }

    // 3. Lettere richieste:
    //    "felice" → f:1, e:2, l:1, i:1, c:1
    //    "mattia" → m:1, a:2, t:2, i:1
    //    Totale  → f:1, e:2, l:1, i:2, c:1, m:1, a:2, t:2
    return counts['f' - 'a'] >= 1 &&
           counts['e' - 'a'] >= 2 &&
           counts['l' - 'a'] >= 1 &&
           counts['i' - 'a'] >= 2 &&
           counts['c' - 'a'] >= 1 &&
           counts['m' - 'a'] >= 1 &&
           counts['a' - 'a'] >= 2 &&
           counts['t' - 'a'] >= 2;
}
JNIEXPORT jboolean JNICALL Java_ch_supsi_dti_backend_license_LicenseChecker_checkLicense
  (JNIEnv *env, jobject obj, jstring key) {
    const char *str = (*env)->GetStringUTFChars(env, key, 0);
    bool result = validate(str);
    (*env)->ReleaseStringUTFChars(env, key, str);
    return result ? JNI_TRUE : JNI_FALSE;
}