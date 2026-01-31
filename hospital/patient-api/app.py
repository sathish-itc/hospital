from flask import Flask, request, jsonify
from flask_cors import CORS
import MySQLdb
import MySQLdb.cursors
import time

app = Flask(__name__)
CORS(app)

# -----------------------------
# Health check (K8s probes)
# -----------------------------
@app.route('/')
def health():
    return 'OK', 200


# -----------------------------
# MySQL configuration
# -----------------------------
DB_CONFIG = {
    'host': 'mysql',          # Kubernetes Service name
    'user': 'user',
    'passwd': 'pass123',
    'db': 'hospital',
    'cursorclass': MySQLdb.cursors.DictCursor
}


def get_db_connection():
    return MySQLdb.connect(**DB_CONFIG)


# -----------------------------
# Initialize DB (Flask 3 safe)
# -----------------------------
def init_db():
    retries = 10
    while retries > 0:
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS patients (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255),
                    age INT
                )
            """)
            conn.commit()
            conn.close()
            print("Database initialized successfully")
            return
        except Exception as e:
            print("Waiting for MySQL...", e)
            retries -= 1
            time.sleep(5)

    print("Database init failed after retries")


# -----------------------------
# APIs
# -----------------------------
@app.route('/patients', methods=['GET'])
def get_patients():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM patients")
    patients = cursor.fetchall()
    conn.close()
    return jsonify(patients)


@app.route('/patients', methods=['POST'])
def add_patient():
    data = request.json
    name = data.get('name')
    age = data.get('age')

    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO patients (name, age) VALUES (%s, %s)",
        (name, age)
    )
    conn.commit()
    patient_id = cursor.lastrowid
    conn.close()

    return jsonify({
        'id': patient_id,
        'name': name,
        'age': age
    })


# -----------------------------
# App start
# -----------------------------
if __name__ == '__main__':
    init_db()  # Safe DB init
    app.run(host='0.0.0.0', port=3000)
