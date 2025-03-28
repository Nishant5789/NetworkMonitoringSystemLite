Welcome to the Network Monitoring System, a robust solution designed to monitor network devices efficiently. This system leverages modern technologies to provide real-time insights into device performance and status. Currently, it supports monitoring Linux devices, with plans to extend support to Windows devices and SNMP-enabled devices in the future.

## Overview

The system is built with a modular architecture, integrating the following core components:

- **Vert.x Framework**: A reactive toolkit for building scalable, event-driven applications in Java.
- **PostgreSQL Database**: A reliable, open-source relational database for storing monitoring data and device configurations.
- **ZeroMQ (ZMQ)**: A high-performance messaging library facilitating communication between the Java-based core system and the Go-based plugin engine.
- **Go-based Linux Plugin Engine**: A lightweight, efficient engine written in Go to handle Linux device monitoring tasks.

## Features

- **Credential Management**: Securely store and manage device credentials.
- **Device Discovery**: Automatically discover devices within the network.
- **Provisioning & Monitoring**: Enable real-time network monitoring and provisioning.
- **Polling & Status Updates**: Retrieve up-to-date network statistics.
- **Availability Checks**: Monitor network availability seamlessly.

## Database Schema

### Credential Table
```sql
CREATE TABLE credential (
          credential_id SERIAL PRIMARY KEY,
          credential_name VARCHAR(255) UNIQUE NOT NULL,
          credential_data JSONB NOT NULL,
          system_type VARCHAR(255) NULL
);
```

### Discovery Table
```sql
CREATE TABLE discovery (
          discovery_id SERIAL PRIMARY KEY,
          credential_id INT NOT NULL,
          ip VARCHAR(255) NOT NULL,
          port INTEGER NOT NULL CHECK (port BETWEEN 0 AND 65535),
          discovery_status VARCHAR(50) NOT NULL DEFAULT 'pending',

          CONSTRAINT fk_discovery_credential
              FOREIGN KEY (credential_id) REFERENCES credential(credential_id)
              ON DELETE RESTRICT
);
```

### Provisioning Table
```sql
 CREATE TABLE IF NOT EXISTS provisioned_objects (
          object_id SERIAL PRIMARY KEY,
          ip VARCHAR(255) UNIQUE NOT NULL,
          credential_id INT NOT NULL,
          pollinterval INT,
          availability_status VARCHAR(255) NOT NULL DEFAULT 'UP',
          CONSTRAINT fk_provisioned_credential
              FOREIGN KEY (credential_id) REFERENCES credential(credential_id)
              ON DELETE RESTRICT
      );
```

### Polling Table
```sql
CREATE TABLE IF NOT EXISTS polling_results (
          ip VARCHAR(255) NOT NULL,
          timestamp BIGINT NOT NULL,
          counters JSONB NOT NULL,
          PRIMARY KEY (ip, timestamp)
      );
```
Here is the updated API documentation with **response examples** included where applicable. Since the original JSON did not provide specific response structures, I have added generic examples based on typical API responses. You can replace these with actual responses from your API when available.

---

## 🔐 **Credentials Module**

| API Name                | Method | URL with Payload                                                                                      | Description                  |
|-------------------------|--------|--------------------------------------------------------------------------------------------------------|------------------------------|
| Get All Credentials     | GET    | `http://localhost:8080/api/credentials/`                                                               | Fetch all credentials        |
| Save Credential         | POST   | `http://localhost:8080/api/credentials/` <br> **Payload:** ```json { "credential_name": "nish123", "credential_data": { "username": "nishant", "password": "password1234" }, "system_type": "linux" } ``` | Save a new credential        |
| Get Credential by ID    | GET    | `http://localhost:8080/api/credentials/{id}`                                                           | Fetch a specific credential  |
| Update Credential by ID | PUT    | `http://localhost:8080/api/credentials/{id}` <br> **Payload:** ```json { "credential_data": { "username": "nishant", "password": "password1234" }, "system_type": "linux" } ``` | Update a credential          |
| Delete Credential by ID | DELETE | `http://localhost:8080/api/credentials/{id}`                                                           | Delete a credential          |

---

## 🔍 **Discovery Module**

| API Name               | Method | URL with Payload                                                                                      | Description                         |
|------------------------|--------|--------------------------------------------------------------------------------------------------------|-------------------------------------|
| Get All Discovery      | GET    | `http://localhost:8080/api/discovery/`                                                                  | Fetch all discovery tasks           |
| Get Discovery by ID    | GET    | `http://localhost:8080/api/discovery/{id}`                                                              | Fetch a discovery task              |
| Add Discovery          | POST   | `http://localhost:8080/api/discovery/` <br> **Payload:** ```json { "ip": "192.168.0.68", "port": 22, "credential_id": 1 } ``` | Create a discovery task             |
| Run Discovery          | POST   | `http://localhost:8080/api/discovery/run` <br> **Payload:** ```json { "discovery_id": 7 } ```          | Run a discovery task                |
| Update Discovery       | PUT    | `http://localhost:8080/api/discovery/{id}` <br> **Payload:** ```json { "ip": "192.168.0.63", "port": 22 } ``` | Update discovery details            |
| Delete Discovery by ID | DELETE | `http://localhost:8080/api/discovery/{id}`                                                              | Delete discovery                    |

---

## 🖥 **Object Module**

| API Name                        | Method | URL with Payload                                                                                      | Description                               |
|---------------------------------|--------|--------------------------------------------------------------------------------------------------------|-------------------------------------------|
| Start Provision                 | POST   | `http://localhost:8080/api/object/provision/` <br> **Payload:** ```json { "ip": "192.168.0.63", "pollinterval": 1000 } ``` | Start provisioning                        |
| Get Polling Data by Object IP   | GET    | `http://localhost:8080/api/object/pollingdata/{ip}`                                                    | Get polling data for an object            |
| Get Object by ID                | GET    | `http://localhost:8080/api/object/{id}`                                                                | Get object information                    |
| Get All Objects                 | GET    | `http://localhost:8080/api/object/`                                                                     | Fetch all objects                         |
| Delete Object by ID             | DELETE | `http://localhost:8080/api/object/{id}`                                                                | Delete a specific object                  |

---

## Communication

### ZeroMQ Server

Uses a ROUTER_DEALER (router-dealer) pattern for communication between the Java client and the Go plugin engine.

Ensures asynchronous message exchange while maintaining responsiveness.

### Client-Server Interaction

1. Java client (Vert.x) sends a request message directly to the ZeroMQ response socket in the Go plugin engine.
2. The Go plugin engine processes the data and sends the response back.
3. The server replies to the client with the processed data.

### Architecture

```
[Vert.x Java Client] --> [ZMQ Delaer-router (tcp://*:5555)] --> [Go Linux Plugin Engine]
|                         |                                |
|                         |                                |
[PostgreSQL]  <-------------------------- [Response] -------------------/
```

- **Vert.x Client**: Handles user requests, sends messages via ZeroMQ, and stores data in PostgreSQL.
- **Go Plugin Engine**: Processes discovery and polling tasks for Linux devices.
- **PostgreSQL**: Stores device configurations and monitoring data.

## Current Support

- **Linux Devices**: Fully supported with SSH-based monitoring.
- **Discovery**: Detects reachable Linux devices.
- **Polling**: Collects system metrics.

## Future Enhancements

- **Windows Devices**: Add support for monitoring via WMI or PowerShell.
- **SNMP Devices**: Integrate SNMP protocol for network equipment monitoring.
- **Enhanced Security**: Encrypt stored credentials and add authentication for ZMQ communication.
- **UI Dashboard**: Develop a web interface for visualizing metrics and managing devices.

## Setup Instructions

### Prerequisites

- Java 17+ (for Vert.x)
- Maven (for dependency management)
- Go 1.18+ (for the plugin engine)
- PostgreSQL 13+
- ZeroMQ library (installed for Go: `go get github.com/pebbe/zmq4`)

### Installation

#### Clone the Repository:

```bash
git clone <repository-url>
cd network-monitoring-system
```

#### Set Up PostgreSQL:

Create a database:

```sql
CREATE DATABASE network_monitoring;
```

#### Build the Go Plugin Engine:

```bash
cd src/server
go build -o linux-plugin-engine
```

#### Run the ZMQ Server:

```bash
./linux-plugin-engine
```

(Default address: `tcp://*:5555`)

#### Build and Run the Vert.x Application:

```bash
cd java-client
mvn clean package
java -jar target/network-monitoring-1.0.0.jar
```

## Contributing

- Report issues or suggest features via GitHub Issues.
- Submit pull requests for enhancements or bug fixes.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

