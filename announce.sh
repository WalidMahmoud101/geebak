#!/bin/bash
# يبعت رسالة لكل اللي عندهم جيبك: بتظهر إشعار وكارت في الرئيسية خلال ساعات.
# الاستخدام:  ./announce.sh "العنوان" "نص الرسالة"
set -euo pipefail
cd "$(dirname "$0")"
TITLE="${1:?اكتب العنوان}"
BODY="${2:-}"
python3 - "$TITLE" "$BODY" <<'PY'
import json,sys
p='docs/news.json'
d=json.load(open(p,encoding='utf-8'))
ids=[m['id'] for m in d['messages']]
d['messages']=[{'id':max(ids,default=0)+1,'title':sys.argv[1],'body':sys.argv[2]}]
json.dump(d,open(p,'w',encoding='utf-8'),ensure_ascii=False,indent=2)
PY
git add docs/news.json
git commit -q -m "News: $TITLE"
git push -q origin HEAD
echo "اتبعتت. هتوصل للناس خلال ساعات، وأول ما يفتحوا التطبيق."
