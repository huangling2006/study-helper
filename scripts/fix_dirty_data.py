import pymysql

db = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='root123', database='study_helper', autocommit=True)
cursor = db.cursor()
template = '{"overall":"历史脏数据", "weakPoints":[]}'
cursor.execute("UPDATE ai_report SET content = %s WHERE id <= 3;", (template,))
print(f"✅ 成功清洗了 {cursor.rowcount} 条历史脏数据，防止 JSON_VALID 报错。")
cursor.close()
db.close()