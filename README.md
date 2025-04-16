# 🚗 Java Sample Application for Tolling Vision

This repository contains a sample Java application that demonstrates how to use [Tolling Vision](https://tollingvision.com) — a Dockerized AI-powered service for tolling companies. Tolling Vision extracts valuable information from vehicle images or image sequences, including:

- **ANPR/ALPR** – Automatic Number Plate Recognition  
- **MMR** – Make and Model Recognition  
- **ADR** – Dangerous Goods Sign Recognition

Tolling Vision is highly scalable and easily integrates into existing systems via **gRPC**. For step-by-step integration, see our [How to use Tolling Vision](https://tollingvision.com/how-to-use-tolling-vision/) guide.

This sample application demonstrates the use of the `analyze` function in the `TollingVisionService`, which is designed for complex analysis scenarios. It processes multiple images per vehicle — such as **front**, **rear**, and **overview** shots — to extract license plate data and MMR details.

---

## ✅ Prerequisites

Before getting started, ensure the following are installed:

- **Java Development Kit (JDK) 17+**
- **Maven** (build tool)
- **Tolling Vision service** (running locally or remotely — see [Get Started](https://tollingvision.com/get-started))

---

## 🚀 Getting Started

### 1. 📦 Clone the Repository

```bash
git clone https://github.com/smartcloudsol/tollingvision-java-sample.git
cd tollingvision-java-sample
```

### 2. 🛠️ Build the Project

Use Maven to compile the project:

```bash
mvn clean install
```

### 3. ▶️ Run the Sample

Run the application with the following command, replacing the placeholders as needed:

```bash
java -cp target:target/* com.smartcloudsolutions.tollingvision.TollingVisionSample <ADDRESS> <SECURED> <THREAD_COUNT> <IMAGE_FOLDER> <RESULT_FILE> <GROUP_PATTERN> <FRONT_PATTERN> <REAR_PATTERN> <OVERVIEW_PATTERN>
```

---

## 🧩 Parameter Descriptions

| Parameter         | Description |
|------------------|-------------|
| `<ADDRESS>`       | IP and port of the Tolling Vision service, in `ip:port` format. |
| `<SECURED>`       | `true` or `false` — whether SSL/TLS encryption is used. |
| `<THREAD_COUNT>`  | Number of parallel requests (max allowed by your license). |
| `<IMAGE_FOLDER>`  | Path to the folder containing images to analyze. |
| `<RESULT_FILE>`   | File path where results will be saved (CSV format). |
| `<GROUP_PATTERN>` | Regex to group images for analysis (e.g., `"^.{7}"` groups by the first 7 characters). The first match group is used as the group key. |
| `<FRONT_PATTERN>` | Regex to detect front images within a group (e.g., `".*front.*"`). |
| `<REAR_PATTERN>`  | Regex to detect rear images within a group (e.g., `".*rear.*"`). |
| `<OVERVIEW_PATTERN>` | Regex to detect overview images (e.g., `".*scene.*"`). |

---

## 📚 Example Usage

```bash
java -cp target:target/* com.smartcloudsolutions.tollingvision.TollingVisionSample 192.168.1.100:443 true 4 ./images ./output/results.csv "^.{7}" ".*front.*" ".*rear.*" ".*scene.*"
```

---

## 📎 Resources

- [Official Website](https://tollingvision.com)
- [How to Use Tolling Vision](https://tollingvision.com/how-to-use-tolling-vision/)
- [Get Started Guide](https://tollingvision.com/get-started)

---

## 🛠️ License

This project is provided for demonstration purposes. See the [LICENSE](./LICENSE) file for more details.
