import json
import os
import psycopg2
from psycopg2.extras import RealDictCursor
import uuid
from datetime import datetime

def get_db_connection():
    return psycopg2.connect(
        host=os.environ['DB_HOST'],
        database=os.environ['DB_NAME'],
        user=os.environ['DB_USER'],
        password=os.environ['DB_PASSWORD'],
        port=os.environ['DB_PORT']
    )

def lambda_handler(event, context):
    http_method = event['httpMethod']
    path = event['path']
    path_parameters = event.get('pathParameters', {}) or {}

    try:
        if http_method == 'GET' and path.endswith('/routes'):
            device_id = path_parameters.get('device_id')
            if device_id:
                return get_route(device_id)
            else:
                return get_routes()
        elif http_method == 'POST' and path.endswith('/routes'):
            return save_route(json.loads(event['body']))
        elif http_method == 'DELETE' and path.endswith('/routes'):
            device_id = path_parameters.get('device_id')
            return delete_route(device_id)
        elif http_method == 'GET' and '/export/' in path:
            device_id = path_parameters.get('device_id')
            return export_route(device_id)
        else:
            return {
                'statusCode': 404,
                'body': json.dumps({'error': 'Not found'})
            }
    except Exception as e:
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }

def get_routes():
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute("SELECT id, device_id, start_timestamp, points FROM routes ORDER BY start_timestamp DESC")
            routes = cur.fetchall()
            return {
                'statusCode': 200,
                'body': json.dumps({
                    'success': True,
                    'data': [dict(route) for route in routes]
                })
            }
    finally:
        conn.close()

def get_route(device_id):
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute("SELECT id, device_id, start_timestamp, points FROM routes WHERE device_id = %s ORDER BY start_timestamp DESC LIMIT 1", (device_id,))
            route = cur.fetchone()
            if route:
                return {
                    'statusCode': 200,
                    'body': json.dumps({
                        'success': True,
                        'data': dict(route)
                    })
                }
            else:
                return {
                    'statusCode': 404,
                    'body': json.dumps({
                        'success': False,
                        'error': 'Route not found'
                    })
                }
    finally:
        conn.close()

def save_route(request_body):
    device_id = request_body['deviceId']
    points = request_body['points']

    if not points:
        return {
            'statusCode': 400,
            'body': json.dumps({
                'success': False,
                'error': 'No points provided'
            })
        }

    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            route_id = str(uuid.uuid4())
            start_timestamp = datetime.now().timestamp() * 1000  # milliseconds

            cur.execute("""
                INSERT INTO routes (id, device_id, start_timestamp, points)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (device_id) DO UPDATE SET
                    start_timestamp = EXCLUDED.start_timestamp,
                    points = EXCLUDED.points
            """, (route_id, device_id, start_timestamp, json.dumps(points)))

            conn.commit()
            return {
                'statusCode': 200,
                'body': json.dumps({
                    'success': True,
                    'data': {'id': route_id}
                })
            }
    finally:
        conn.close()

def delete_route(device_id):
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM routes WHERE device_id = %s", (device_id,))
            conn.commit()
            return {
                'statusCode': 200,
                'body': json.dumps({
                    'success': True
                })
            }
    finally:
        conn.close()

def export_route(device_id):
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute("SELECT * FROM routes WHERE device_id = %s ORDER BY start_timestamp DESC LIMIT 1", (device_id,))
            route = cur.fetchone()
            if route:
                # Generate SQL dump format
                export_data = f"""-- Miles Tracker Route Export
-- Device ID: {route['device_id']}
-- Start Time: {datetime.fromtimestamp(route['start_timestamp']/1000)}
-- Points: {len(route['points'])}

INSERT INTO routes (id, device_id, start_timestamp, points) VALUES
('{route['id']}', '{route['device_id']}', {route['start_timestamp']}, '{json.dumps(route['points'])}');
"""
                return {
                    'statusCode': 200,
                    'body': export_data,
                    'headers': {
                        'Content-Type': 'text/plain',
                        'Content-Disposition': f'attachment; filename="route_export_{device_id}.sql"'
                    }
                }
            else:
                return {
                    'statusCode': 404,
                    'body': json.dumps({
                        'success': False,
                        'error': 'Route not found'
                    })
                }
    finally:
        conn.close()
