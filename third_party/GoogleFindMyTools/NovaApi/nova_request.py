#
#  GoogleFindMyTools - A set of tools to interact with the Google Find My API
#  Copyright © 2024 Leon Böttger. All rights reserved.
#

import binascii
import requests
from bs4 import BeautifulSoup

from Auth.aas_token_retrieval import get_aas_token
from Auth.adm_token_retrieval import get_adm_token
from Auth.username_provider import get_username


def nova_request(api_scope, hex_payload):
    url = "https://android.googleapis.com/nova/" + api_scope

    android_device_manager_oauth_token = get_adm_token(get_username())

    headers = {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Authorization": "Bearer " + android_device_manager_oauth_token,
        "Accept-Language": "en-US",
        "User-Agent": "fmd/20006320; gzip"
    }

    payload = binascii.unhexlify(hex_payload)

    # (connect, read) timeout: never let a stalled socket hang the whole locate forever.
    # The read only covers Google's acknowledgement here; the location itself arrives
    # later as an FCM push, which the caller waits for separately.
    response = requests.post(url, headers=headers, data=payload, timeout=(10, 30))

    if response.status_code == 200:
        return response.content.hex()
    else:
        # Surface the real reason instead of returning None and later timing out blankly
        # (e.g. a 401 means the Google token expired / the account needs re-login).
        soup = BeautifulSoup(response.text, 'html.parser')
        detail = soup.get_text().strip() or response.reason or ""
        print("[NovaRequest] Error %s: %s" % (response.status_code, detail))
        raise RuntimeError("Google rejected the request (HTTP %s): %s"
                           % (response.status_code, detail[:200]))


if __name__ == '__main__':
    print(get_aas_token())