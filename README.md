# DeskMate - Remote PC Control

A lightweight and efficient solution for controlling and managing your computer remotely. The application runs a background client agent on the host PC to receive and execute remote commands, automate input events, and monitor system tasks.

---

## 📌 Features

- **Lightweight Background Agent (`pc_agent.py`):** Runs seamlessly on the host machine to listen for incoming control events.
- **Input & System Control:** Supports input automation (mouse and keyboard actions), screen monitoring, and remote power/system management.
- **Environment-Based Configuration:** Secure and flexible configuration management using `.env` files.
- **Extensible Architecture:** Designed to integrate easily with mobile apps, web dashboards, or cloud relays (e.g., WebSockets, Firebase, or HTTP endpoints).

---

## 🛠 Tech Stack

- **Language:** Python 3.8+
- **Configuration:** `python-dotenv`
- **Automation & Networking:** OS automation and network communication libraries

---

## 📁 Project Structure

```text
remote-pc-control/
├── pc_agent.py          # Primary background client agent
├── .env                 # Environment secrets and credentials (ignored in Git)
├── .env.example         # Example template for environment configuration
├── requirements.txt     # Python package dependencies
└── README.md            # Project documentation and setup guide
```

---

## ⚙️ Prerequisites

- **Python 3.8** or higher installed on your system.
- **pip** package manager.
- Administrative privileges may be required depending on the OS-level actions executed.

---

## 🚀 Installation & Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd "remote-pc-control"
```

### 2. Create and Activate a Virtual Environment

- **Windows:**
  ```bash
  python -m venv venv
  venv\Scripts\activate
  ```
- **macOS / Linux:**
  ```bash
  python3 -m venv venv
  source venv/bin/activate
  ```

### 3. Install Dependencies

```bash
pip install -r requirements.txt
```

### 4. Configure Environment Variables

Create your local `.env` configuration by copying the example file:

```bash
cp .env.example .env
```

Open `.env` and fill in the required parameters (e.g., host IP, port numbers, auth tokens, or broker credentials).

### 5. Launch the Client Agent

```bash
python pc_agent.py
```

---

## 🔒 Security Best Practices

- **Never commit `.env` files** containing live tokens, keys, or passwords to public repositories. Keep `.env` in your `.gitignore`.
- Run the agent on trusted local networks or through secure tunnels (such as a private VPN or authenticated TLS/WebSocket connections).
- Configure your OS firewall to allow inbound traffic only from authorized endpoints or subnets.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) (or your preferred license).
