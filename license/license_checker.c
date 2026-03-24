#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#ifdef _WIN32
    #include <windows.h>
#else
    #include <unistd.h>
    #include <pwd.h>
#endif

#define LICENSE_FILE         "license.key"
#define MAX_USERNAME         256
#define MAX_LICENSE          64
#define MAX_HASH             65   /* SHA-256 hex digest = 64 chars + '\0' */
#define MAX_LINE             (MAX_LICENSE + MAX_HASH + 4)
#define MAX_LICENSES         1024
#define SHA256_DIGEST_LENGTH 32

/* --------------------------------------------------------------------------
 * Pure-C SHA-256 — no external libraries required.
 * -------------------------------------------------------------------------- */
#define ROTR(x,n)  (((x) >> (n)) | ((x) << (32-(n))))
#define CH(x,y,z)  (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x,y,z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x)     (ROTR(x,2)  ^ ROTR(x,13) ^ ROTR(x,22))
#define EP1(x)     (ROTR(x,6)  ^ ROTR(x,11) ^ ROTR(x,25))
#define SIG0(x)    (ROTR(x,7)  ^ ROTR(x,18) ^ ((x) >> 3))
#define SIG1(x)    (ROTR(x,17) ^ ROTR(x,19) ^ ((x) >> 10))

static const uint32_t K[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,
    0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,
    0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,
    0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,
    0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,
    0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,
    0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,
    0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,
    0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

static void sha256_raw(const unsigned char *data, size_t len,
                       unsigned char out[SHA256_DIGEST_LENGTH])
{
    uint32_t h[8] = {
        0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,
        0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19
    };

    size_t msg_len = len + 1 + 8;
    if (msg_len % 64 != 0) msg_len += 64 - (msg_len % 64);

    unsigned char *msg = (unsigned char *)calloc(msg_len, 1);
    memcpy(msg, data, len);
    msg[len] = 0x80;

    uint64_t bit_len = (uint64_t)len * 8;
    for (int i = 0; i < 8; i++)
        msg[msg_len - 1 - i] = (unsigned char)(bit_len >> (i * 8));

    for (size_t i = 0; i < msg_len; i += 64) {
        uint32_t w[64];
        for (int j = 0; j < 16; j++)
            w[j] = ((uint32_t)msg[i+j*4]   << 24) |
                   ((uint32_t)msg[i+j*4+1] << 16) |
                   ((uint32_t)msg[i+j*4+2] <<  8) |
                   ((uint32_t)msg[i+j*4+3]);
        for (int j = 16; j < 64; j++)
            w[j] = SIG1(w[j-2]) + w[j-7] + SIG0(w[j-15]) + w[j-16];

        uint32_t a=h[0],b=h[1],c=h[2],d=h[3],
                 e=h[4],f=h[5],g=h[6],hh=h[7];

        for (int j = 0; j < 64; j++) {
            uint32_t t1 = hh + EP1(e) + CH(e,f,g) + K[j] + w[j];
            uint32_t t2 = EP0(a) + MAJ(a,b,c);
            hh=g; g=f; f=e; e=d+t1;
            d=c;  c=b; b=a; a=t1+t2;
        }
        h[0]+=a; h[1]+=b; h[2]+=c; h[3]+=d;
        h[4]+=e; h[5]+=f; h[6]+=g; h[7]+=hh;
    }
    free(msg);

    for (int i = 0; i < 8; i++) {
        out[i*4]   = (h[i] >> 24) & 0xff;
        out[i*4+1] = (h[i] >> 16) & 0xff;
        out[i*4+2] = (h[i] >>  8) & 0xff;
        out[i*4+3] =  h[i]        & 0xff;
    }
}

static void sha256_hex(const char *input, char *out)
{
    unsigned char hash[SHA256_DIGEST_LENGTH];
    sha256_raw((const unsigned char *)input, strlen(input), hash);
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++)
        sprintf(out + (i * 2), "%02x", hash[i]);
    out[MAX_HASH - 1] = '\0';
}

/* --------------------------------------------------------------------------
 * CSV helpers
 * -------------------------------------------------------------------------- */

typedef struct {
    char key[MAX_LICENSE];
    char hash[MAX_HASH];   /* empty string = not yet activated */
} LicenseEntry;

static int load_licenses(LicenseEntry entries[], int max)
{
    FILE *f = fopen(LICENSE_FILE, "r");
    if (!f) {
        fprintf(stderr, "[ERROR] Cannot open license file '%s'.\n", LICENSE_FILE);
        return -1;
    }

    int count = 0;
    char line[MAX_LINE];

    while (count < max && fgets(line, sizeof(line), f)) {
        line[strcspn(line, "\r\n")] = '\0';
        if (line[0] == '\0') continue;

        char *comma = strchr(line, ',');
        if (!comma) {
            strncpy(entries[count].key, line, MAX_LICENSE - 1);
            entries[count].key[MAX_LICENSE - 1] = '\0';
            entries[count].hash[0] = '\0';
        } else {
            size_t key_len = (size_t)(comma - line);
            strncpy(entries[count].key, line, key_len);
            entries[count].key[key_len] = '\0';
            strncpy(entries[count].hash, comma + 1, MAX_HASH - 1);
            entries[count].hash[MAX_HASH - 1] = '\0';
        }
        count++;
    }

    fclose(f);
    return count;
}

static int save_licenses(const LicenseEntry entries[], int count)
{
    FILE *f = fopen(LICENSE_FILE, "w");
    if (!f) {
        fprintf(stderr, "[ERROR] Cannot write to license file '%s'.\n", LICENSE_FILE);
        return 0;
    }

    for (int i = 0; i < count; i++) {
        if (entries[i].hash[0] != '\0')
            fprintf(f, "%s,%s\n", entries[i].key, entries[i].hash);
        else
            fprintf(f, "%s,\n", entries[i].key);
    }

    fclose(f);
    return 1;
}

/* --------------------------------------------------------------------------
 * OS username
 * -------------------------------------------------------------------------- */
static int get_local_username(char *out, size_t out_size)
{
#ifdef _WIN32
    DWORD sz = (DWORD)out_size;
    return GetUserNameA(out, &sz);
#else
    struct passwd *pw = getpwuid(getuid());
    if (!pw) return 0;
    strncpy(out, pw->pw_name, out_size - 1);
    out[out_size - 1] = '\0';
    return 1;
#endif
}

/* --------------------------------------------------------------------------
 * Core logic
 *
 *   Key NOT FOUND           → error: invalid license
 *   Key found, no hash      → activate: write SHA-256(username+key), save
 *   Key found, hash matches → already activated for this machine, valid
 *   Key found, hash differs → activated for a different machine, invalid
 * -------------------------------------------------------------------------- */
static int process_license(const char *input_key)
{
    LicenseEntry entries[MAX_LICENSES];
    int count = load_licenses(entries, MAX_LICENSES);
    if (count < 0) return 1;

    char username[MAX_USERNAME] = {0};
    if (!get_local_username(username, sizeof(username))) {
        fprintf(stderr, "[ERROR] Could not retrieve local username.\n");
        return 1;
    }

    char combined[MAX_USERNAME + MAX_LICENSE] = {0};
    char expected_hash[MAX_HASH] = {0};
    snprintf(combined, sizeof(combined), "%s%s", username, input_key);
    sha256_hex(combined, expected_hash);

    for (int i = 0; i < count; i++) {
        if (strcmp(entries[i].key, input_key) != 0) continue;

        /* ── Key found ── */

        if (entries[i].hash[0] == '\0') {
            /* Not yet activated → activate now */
            strncpy(entries[i].hash, expected_hash, MAX_HASH - 1);
            if (!save_licenses(entries, count)) return 1;
            printf("[OK] License activated successfully. Welcome, %s!\n", username);
            return 0;
        }

        if (strncmp(entries[i].hash, expected_hash, MAX_HASH - 1) == 0) {
            /* Already activated for this machine */
            printf("[OK] License valid. Welcome, %s!\n", username);
            return 0;
        }

        /* Activated for a different machine */
        fprintf(stderr, "[ERROR] Invalid license: key already in use on another machine.\n");
        return 1;
    }

    fprintf(stderr, "[ERROR] Invalid license: key not recognized.\n");
    return 1;
}

/* --------------------------------------------------------------------------
 * main
 * Usage:  license_checker <LICENSE-KEY>
 * -------------------------------------------------------------------------- */
int main(int argc, char *argv[])
{
    if (argc != 2) {
        fprintf(stderr, "Usage: %s <LICENSE-KEY>\n", argv[0]);
        return 1;
    }

    return process_license(argv[1]);
}