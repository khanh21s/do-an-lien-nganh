#!/usr/bin/env bash
# =============================================================================
# Simulate a signed VNPay IPN / return callback locally (spec SUB-01/02, Q8).
# =============================================================================
# VNPay cannot reach localhost, so this script builds the query string VNPay would
# send, signs it with HMAC-SHA512 using VNPAY_SECRET (same algorithm as
# VNPayService: params sorted by key, form-URL-encoded, vnp_SecureHash appended)
# and calls the gateway.
#
# Usage:
#   scripts/vnpay-ipn-test.sh <txnRef> <amountVnd> [responseCode] [endpoint]
#     txnRef       transactionId returned by POST /subscriptions/create-payment
#     amountVnd    amount in VND (the script sends vnp_Amount = amountVnd * 100);
#                  pass a different amount to test the "wrong amount" path (RspCode 04)
#     responseCode 00 = success (default), anything else = failed payment
#     endpoint     vnpay-ipn (default) or payment-callback
#   Env: VNPAY_SECRET (read from .env when not set), GATEWAY_URL (default http://localhost:8000)
#   Set BAD_SIGNATURE=1 to send a tampered signature (expect RspCode 97).
# =============================================================================
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ "$#" -lt 2 ]; then
  sed -n '/^# Usage:/,/BAD_SIGNATURE=1/p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi
TXN_REF="$1"; AMOUNT="$2"; RESP="${3:-00}"; ENDPOINT="${4:-vnpay-ipn}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"

if [ -z "${VNPAY_SECRET:-}" ] && [ -f "$ROOT/.env" ]; then
  VNPAY_SECRET="$(grep -E '^VNPAY_SECRET=' "$ROOT/.env" | tail -1 | cut -d= -f2-)"
fi
if [ -z "${VNPAY_SECRET:-}" ]; then
  echo "VNPAY_SECRET is empty (set it in .env and restart subscription-service)" >&2
  exit 1
fi
if [ -z "${VNPAY_MERCHANT_ID:-}" ] && [ -f "$ROOT/.env" ]; then
  VNPAY_MERCHANT_ID="$(grep -E '^VNPAY_MERCHANT_ID=' "$ROOT/.env" | tail -1 | cut -d= -f2-)"
fi

QUERY="$(VNPAY_SECRET="$VNPAY_SECRET" TMN="${VNPAY_MERCHANT_ID:-DEMO}" BAD="${BAD_SIGNATURE:-0}" \
  python3 -I - "$TXN_REF" "$AMOUNT" "$RESP" <<'PY'
import hashlib, hmac, os, sys, time
from urllib.parse import urlencode
txn, amount, resp = sys.argv[1], int(sys.argv[2]), sys.argv[3]
params = {
    "vnp_Amount": str(amount * 100),
    "vnp_BankCode": "NCB",
    "vnp_CardType": "ATM",
    "vnp_OrderInfo": "Parroto local IPN test",
    "vnp_PayDate": time.strftime("%Y%m%d%H%M%S"),
    "vnp_ResponseCode": resp,
    "vnp_TmnCode": os.environ["TMN"],
    "vnp_TransactionNo": str(int(time.time())),
    "vnp_TransactionStatus": resp,
    "vnp_TxnRef": txn,
}
data = urlencode(sorted(params.items()))
sig = hmac.new(os.environ["VNPAY_SECRET"].encode(), data.encode(), hashlib.sha512).hexdigest()
if os.environ.get("BAD") == "1":
    sig = ("0" if sig[0] != "0" else "1") + sig[1:]
print(data + "&vnp_SecureHash=" + sig)
PY
)"

URL="$GATEWAY_URL/subscriptions/$ENDPOINT?$QUERY"
echo "GET $URL" >&2
curl -sS -i "$URL"
echo
