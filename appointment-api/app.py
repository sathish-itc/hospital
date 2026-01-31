from flask import Flask, request, jsonify
from flask_cors import CORS
import MySQLdb
import MySQLdb.cursors
import time

app = Flask(__name__)
CORS(app)

# -----------------------------
# Health check
# -----------------------------
@app.route('/')
def health():
    return 'OK', 200


# -----------------------------
# MySQL configuration
# -----------------------------
DB_CONFIG = {
    'host': 'mysql',
    'user': 'user',
    'passwd': 'pass123',
    'db': 'hospital',
    'cursorclass': MySQLdb.cursors.DictCursor
}

def get_db_connection():
    return MySQLdb.connect(**DB_CONFIG)


# -----------------------------
# Safe DB init
# -----------------------------
def init_db():
    retries = 10
    while retries > 0:
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS appointments (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    patient_id INT,
                    doctor VARCHAR(255),
                    date DATE,
                    time TIME
                )
            """)
            conn.commit()
            conn.close()
            print("Appointments table ready")
            return
        except Exception as e:
            print("Waiting for MySQL...", e)
            retries -= 1
            time.sleep(5)


# -----------------------------
# APIs
# -----------------------------
@app.route('/appointments', methods=['GET'])
def get_appointments():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT
            id,
            patient_id,
            doctor,
            DATE_FORMAT(date, '%Y-%m-%d') AS date,
            TIME_FORMAT(time, '%H:%i') AS time
        FROM appointments
    """)
    appointments = cursor.fetchall()
    conn.close()
    return jsonify(appointments)


@app.route('/appointments', methods=['POST'])
def add_appointment():
    data = request.json
    patient_id = data.get('patient_id')
    doctor = data.get('doctor')
    date = data.get('date')
    time = data.get('time')

    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO appointments (patient_id, doctor, date, time) VALUES (%s, %s, %s, %s)",
        (patient_id, doctor, date, time)
    )
    conn.commit()
    appointment_id = cursor.lastrowid
    conn.close()

    return jsonify({
        'id': appointment_id,
        'patient_id': patient_id,
        'doctor': doctor,
        'date': date,
        'time': time
    })


# -----------------------------
# App start
# -----------------------------
if __name__ == '__main__':
    init_db()
    app.run(host='0.0.0.0', port=3001)
