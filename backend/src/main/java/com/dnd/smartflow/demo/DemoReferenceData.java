package com.dnd.smartflow.demo;

import java.util.List;

/**
 * Fictional reference data of the demonstration organization "Atlas Industries Group" (FMCG, Algeria).
 * Any resemblance to existing companies is unintended; geographic coordinates are city centres.
 */
final class DemoReferenceData {
    private DemoReferenceData() {
    }

    record SiteDef(String code, String name, String type, String address, String city, String region, double lat, double lon, String manager) {
    }

    record City(String name, String region, double lat, double lon, String hub) {
    }

    record ProductDef(String sku, String name, String category, String family, String line, String unit, double weightKg, int shelfLifeDays,
                      int price, double co2PerUnit, int baseWeekly, int reorderPoint) {
    }

    record SupplierDef(String code, String name, String category, String country, String city, String status, double quality, double onTime, String certs) {
    }

    static final List<SiteDef> SITES = List.of(
            new SiteDef("CST", "Constantine Industrial Complex", "PRODUCTION", "Zone industrielle Palma, BP 120", "Constantine", "Constantine", 36.3387, 6.6266, "Hichem Bouzid"),
            new SiteDef("ALG", "Algiers Distribution Center", "DISTRIBUTION", "Zone d'activités Oued Smar, lot 14", "Alger", "Alger", 36.7106, 3.1672, "Leila Mansouri"),
            new SiteDef("ORN", "Oran Regional Warehouse", "WAREHOUSE", "Zone industrielle Es-Sénia, ilot 7", "Oran", "Oran", 35.6491, -0.6237, "Abdelkader Benamar"),
            new SiteDef("STF", "Sétif Production Plant", "PRODUCTION", "Zone industrielle Sétif-Est, lot 33", "Sétif", "Sétif", 36.1690, 5.3930, "Salim Djeddi"));

    static final List<City> CITIES = List.of(
            new City("Alger", "Alger", 36.7538, 3.0588, "ALG"), new City("Blida", "Blida", 36.4700, 2.8277, "ALG"),
            new City("Boumerdès", "Boumerdès", 36.7664, 3.4772, "ALG"), new City("Tizi Ouzou", "Tizi Ouzou", 36.7118, 4.0459, "ALG"),
            new City("Tipaza", "Tipaza", 36.5897, 2.4475, "ALG"), new City("Médéa", "Médéa", 36.2642, 2.7539, "ALG"),
            new City("Bouira", "Bouira", 36.3749, 3.9020, "ALG"), new City("Oran", "Oran", 35.6971, -0.6308, "ORN"),
            new City("Mostaganem", "Mostaganem", 35.9312, 0.0891, "ORN"), new City("Tlemcen", "Tlemcen", 34.8783, -1.3150, "ORN"),
            new City("Sidi Bel Abbès", "Sidi Bel Abbès", 35.1899, -0.6308, "ORN"), new City("Mascara", "Mascara", 35.3966, 0.1402, "ORN"),
            new City("Aïn Témouchent", "Aïn Témouchent", 35.2977, -1.1404, "ORN"), new City("Constantine", "Constantine", 36.3650, 6.6147, "CST"),
            new City("Annaba", "Annaba", 36.9000, 7.7667, "CST"), new City("Skikda", "Skikda", 36.8762, 6.9092, "CST"),
            new City("Mila", "Mila", 36.4503, 6.2644, "CST"), new City("Guelma", "Guelma", 36.4621, 7.4261, "CST"),
            new City("Jijel", "Jijel", 36.8206, 5.7667, "CST"), new City("Batna", "Batna", 35.5559, 6.1741, "CST"),
            new City("Oum El Bouaghi", "Oum El Bouaghi", 35.8754, 7.1135, "CST"), new City("Sétif", "Sétif", 36.1911, 5.4137, "STF"),
            new City("Béjaïa", "Béjaïa", 36.7525, 5.0564, "STF"), new City("Bordj Bou Arréridj", "Bordj Bou Arréridj", 36.0732, 4.7630, "STF"),
            new City("M'Sila", "M'Sila", 35.7058, 4.5419, "STF"));

    static final List<ProductDef> PRODUCTS = List.of(
            new ProductDef("ATL-PA-101", "Spaghetti Atlas 500 g (carton 20)", "Pâtes alimentaires", "PASTA", "L1", "CARTON", 10.4, 730, 1450, 14.6, 230, 600),
            new ProductDef("ATL-PA-102", "Spaghettini Atlas 500 g (carton 20)", "Pâtes alimentaires", "PASTA", "L1", "CARTON", 10.4, 730, 1450, 14.6, 120, 300),
            new ProductDef("ATL-PA-103", "Tagliatelles Atlas 500 g (carton 20)", "Pâtes alimentaires", "PASTA", "L1", "CARTON", 10.4, 730, 1590, 14.8, 70, 180),
            new ProductDef("ATL-PA-201", "Coquillettes Atlas 500 g (carton 20)", "Pâtes alimentaires", "PASTA", "L2", "CARTON", 10.4, 730, 1450, 14.6, 180, 450),
            new ProductDef("ATL-PA-202", "Penne Atlas 500 g (carton 20)", "Pâtes alimentaires", "PASTA", "L2", "CARTON", 10.4, 730, 1490, 14.6, 110, 280),
            new ProductDef("ATL-PA-203", "Torsades Atlas 500 g (carton 20)", "Pâtes alimentaires", "PASTA", "L2", "CARTON", 10.4, 730, 1490, 14.6, 90, 220),
            new ProductDef("ATL-PA-204", "Langues d'oiseau Atlas 500 g (carton 20)", "Pâtes alimentaires", "PASTA", "L2", "CARTON", 10.4, 730, 1420, 14.5, 85, 200),
            new ProductDef("ATL-CS-301", "Couscous fin Atlas 1 kg (carton 12)", "Couscous & semoule", "COUSCOUS", "L3", "CARTON", 12.4, 540, 1850, 17.2, 200, 500),
            new ProductDef("ATL-CS-302", "Couscous moyen Atlas 1 kg (carton 12)", "Couscous & semoule", "COUSCOUS", "L3", "CARTON", 12.4, 540, 1850, 17.2, 160, 420),
            new ProductDef("ATL-CS-303", "Couscous gros Atlas 1 kg (carton 12)", "Couscous & semoule", "COUSCOUS", "L3", "CARTON", 12.4, 540, 1890, 17.4, 60, 150),
            new ProductDef("ATL-CS-304", "Berkoukes Atlas 500 g (carton 20)", "Couscous & semoule", "COUSCOUS", "L3", "CARTON", 10.4, 540, 1690, 14.9, 45, 120),
            new ProductDef("ATL-CS-305", "Semoule fine Atlas 1 kg (carton 12)", "Couscous & semoule", "COUSCOUS", "L3", "CARTON", 12.4, 365, 1520, 16.1, 140, 350),
            new ProductDef("ATL-BS-401", "Biscuit Atlas Choco 50 g (carton 48)", "Biscuits", "BISCUITS", "L4", "CARTON", 2.7, 270, 1250, 6.8, 260, 650),
            new ProductDef("ATL-BS-402", "Sablés Atlas 200 g (carton 24)", "Biscuits", "BISCUITS", "L4", "CARTON", 5.2, 300, 2160, 9.9, 120, 300),
            new ProductDef("ATL-BS-403", "Gaufrettes vanille Atlas 150 g (carton 24)", "Biscuits", "BISCUITS", "L4", "CARTON", 3.9, 300, 1920, 8.1, 95, 240),
            new ProductDef("ATL-BS-404", "Cookies pépites Atlas 120 g (carton 24)", "Biscuits", "BISCUITS", "L4", "CARTON", 3.3, 240, 2280, 8.6, 75, 190),
            new ProductDef("ATL-TS-501", "Papier hygiénique Atlas Soft 4 rouleaux (colis 12)", "Papier hygiénique", "TISSUE", "L5", "COLIS", 4.1, 1095, 1650, 5.9, 210, 520),
            new ProductDef("ATL-TS-502", "Papier hygiénique Atlas Soft 12 rouleaux (colis 4)", "Papier hygiénique", "TISSUE", "L5", "COLIS", 4.3, 1095, 1890, 6.1, 150, 380),
            new ProductDef("ATL-TS-601", "Mouchoirs Atlas boîte 100 (carton 24)", "Mouchoirs & essuie-tout", "TISSUE", "L6", "CARTON", 5.6, 1095, 2640, 7.4, 130, 320),
            new ProductDef("ATL-TS-602", "Essuie-tout Atlas 2 rouleaux (colis 12)", "Mouchoirs & essuie-tout", "TISSUE", "L6", "COLIS", 3.8, 1095, 1560, 5.5, 110, 280),
            new ProductDef("ATL-TS-603", "Serviettes de table Atlas 100 (carton 30)", "Mouchoirs & essuie-tout", "TISSUE", "L6", "CARTON", 4.9, 1095, 1950, 6.4, 65, 160),
            new ProductDef("ATL-BB-701", "Couches Atlas Baby T3 x48 (carton 4)", "Couches bébé", "DIAPERS", "L7", "CARTON", 8.2, 1095, 5400, 21.5, 140, 350),
            new ProductDef("ATL-BB-702", "Couches Atlas Baby T4 x44 (carton 4)", "Couches bébé", "DIAPERS", "L7", "CARTON", 8.6, 1095, 5400, 22.4, 125, 320),
            new ProductDef("ATL-BB-703", "Couches Atlas Baby T5 x40 (carton 4)", "Couches bébé", "DIAPERS", "L7", "CARTON", 8.9, 1095, 5600, 23.1, 90, 230));

    static final List<SupplierDef> SUPPLIERS = List.of(
            new SupplierDef("SUP-001", "Céréales des Hauts Plateaux", "Blé dur & semoule", "Algérie", "Sétif", "QUALIFIED", 92, 96, "ISO 9001, ISO 22000"),
            new SupplierDef("SUP-002", "Moulins du Rhumel", "Semoule", "Algérie", "Constantine", "QUALIFIED", 88, 93, "ISO 9001"),
            new SupplierDef("SUP-003", "PlastEmball SARL", "Films d'emballage", "Algérie", "Blida", "CONDITIONAL", 71, 84, "—"),
            new SupplierDef("SUP-004", "Cartonnerie de l'Est", "Cartons ondulés", "Algérie", "Annaba", "QUALIFIED", 90, 95, "ISO 9001, FSC"),
            new SupplierDef("SUP-005", "Nordic Pulp AB", "Pâte de cellulose", "Suède", "Sundsvall", "QUALIFIED", 95, 97, "ISO 9001, ISO 14001, FSC"),
            new SupplierDef("SUP-006", "Iberia Pulp SA", "Pâte de cellulose", "Espagne", "Huelva", "QUALIFIED", 86, 91, "ISO 9001, PEFC"),
            new SupplierDef("SUP-007", "Absorb Chem GmbH", "Polymère super-absorbant", "Allemagne", "Marl", "QUALIFIED", 93, 94, "ISO 9001, ISO 14001"),
            new SupplierDef("SUP-008", "Cacao Ivoire SA", "Cacao & chocolat", "Côte d'Ivoire", "San-Pédro", "CONDITIONAL", 78, 86, "Rainforest Alliance"),
            new SupplierDef("SUP-009", "Sucrerie Méditerranée", "Sucre", "Algérie", "Béjaïa", "QUALIFIED", 89, 95, "ISO 22000"),
            new SupplierDef("SUP-010", "Huiles du Sahel", "Huiles végétales", "Algérie", "Oran", "PENDING", 80, 88, "—"),
            new SupplierDef("SUP-011", "ColleTech", "Adhésifs hot-melt", "Tunisie", "Sfax", "QUALIFIED", 87, 92, "ISO 9001"),
            new SupplierDef("SUP-012", "Encres & Codage DZ", "Encres de marquage", "Algérie", "Alger", "SUSPENDED", 62, 79, "—"));

    static final List<String> FIRST_NAMES_M = List.of("Mohamed", "Ahmed", "Karim", "Yacine", "Sofiane", "Rachid", "Farid", "Hichem", "Nabil", "Amine",
            "Walid", "Mourad", "Samir", "Bilal", "Riad", "Omar", "Khaled", "Abdelkader", "Lotfi", "Mehdi", "Hamza", "Tarek", "Redouane", "Ilyes", "Anis");
    static final List<String> FIRST_NAMES_F = List.of("Amina", "Nadia", "Lynda", "Samia", "Leila", "Meriem", "Sarah", "Imane", "Nesrine", "Fatima",
            "Asma", "Khadidja", "Yasmine", "Hanane", "Souad", "Rania", "Wafa", "Lamia");
    static final List<String> LAST_NAMES = List.of("Benali", "Bensalem", "Khelifi", "Boumediene", "Belkacem", "Hamidi", "Ouali", "Saadi", "Zerrouki",
            "Ait Ahmed", "Bouzid", "Mansouri", "Benamar", "Djeddi", "Haddad", "Cherif", "Boudiaf", "Messaoudi", "Brahimi", "Mebarki", "Touati", "Kaci",
            "Rahmani", "Belaid", "Larbi", "Saidi", "Chaouch", "Ferhat", "Guerroudj", "Hadj Ali", "Lounis", "Meziane", "Slimani", "Yahiaoui", "Zitouni",
            "Bouchama", "Amrani", "Benyahia", "Kerroum", "Laib");
    static final List<String> SHOP_PREFIX = List.of("Supérette", "Grossiste", "Hypermarché", "Supermarché", "Distribution", "Épicerie", "Mini-market", "Dépôt");
    static final List<String> SHOP_NAMES = List.of("El Baraka", "Nour", "El Wiam", "Tassili", "Frères Mansouri", "El Amel", "Yasmine", "Djurdjura",
            "Méditerranée", "Ennakhil", "Es-Salam", "El Fadjr", "Cirta", "Hoggar", "El Bahia", "Ifri", "Rym", "El Khir", "Atlas Sud", "Zeralda",
            "Seybouse", "Numidia", "Tafna", "El Hidhab", "Babors", "Chelia", "Aurès", "Mazafran", "El Hamma", "Sidi Rached");
}
