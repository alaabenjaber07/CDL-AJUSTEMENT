# Application Ajustement CDL

Une application Full-Stack (Angular 16 + Spring Boot 2.7) pour la gestion dynamique des tables dans Oracle, sans avoir besoin de développer et compiler du code pour chaque table.

## 🚀 Fonctionnalités Clés
- **Grille Dynamique (Inline Edit)** : Visualisez, modifiez, ajoutez ou supprimez des lignes directement dans une grille Material Design. La structure s'adapte automatiquement à n'importe quelle table Oracle.
- **Nouvelles Tables à la volée** : Créez de nouvelles tables Oracle depuis l'interface UI (nom, colonnes, données).
- **Audit Centralisé** : Toutes vos modifications (INSERT, UPDATE, DELETE) sont interceptées et tracées en JSON dans la table `CDL_AUDIT_LOG`.

## 🛠️ Prérequis
- Java 1.8 (JDK 8)
- Maven 3.x
- Node.js (v16 ou +) et npm
- Base de données Oracle CDL19C accessible (`dbdept-scan:1521/DEPTABT`)

## 🏃 Comment Lancer l'Application

### 1. Lancer le Backend (Spring Boot)
1. Ouvrez un terminal dans le dossier `backend`.
2. Vérifiez le fichier `src/main/resources/application.properties` (si vous avez besoin d'ajuster le user/password DB).
3. Exécutez :
   ```bash
   mvn spring-boot:run
   ```
   *L'API sera disponible sur `http://localhost:8080/api`.*
   *(La table CDL_AUDIT_LOG sera créée automatiquement par Hibernate au premier lancement via `ddl-auto=update`).*

### 2. Lancer le Frontend (Angular)
1. Ouvrez un terminal dans le dossier `frontend`.
2. Installez les packages (et Angular Material) :
   ```bash
   npm install
   npm install @angular/material @angular/cdk
   ```
3. Démarrez le serveur Web :
   ```bash
   npm run start
   ```
   *L'UI complète et design Material s'ouvrira sur `http://localhost:4200`.*
