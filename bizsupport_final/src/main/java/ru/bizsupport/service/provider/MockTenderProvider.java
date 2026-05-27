package ru.bizsupport.service.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.bizsupport.entity.Tender;
import ru.bizsupport.entity.TenderLawType;
import ru.bizsupport.entity.TenderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Mock-провайдер с реалистичными тендерами для разработки и демонстрации.
 * Данные имитируют реальные закупки из ЕИС zakupki.gov.ru:
 *   - Реалистичные заказчики (государственные, муниципальные, корпоративные)
 *   - Реальные категории и ОКПД2
 *   - Регистрационные номера в формате ЕИС
 *   - Реалистичные цены и сроки
 */
@Component
public class MockTenderProvider implements TenderProvider {

    @Override
    public String getName() { return "mock"; }

    private static final String[] REGIONS = {
            "Москва", "Санкт-Петербург", "Нижегородская обл.", "Свердловская обл.",
            "Татарстан", "Краснодарский край", "Новосибирская обл.", "Башкортостан",
            "Ростовская обл.", "Самарская обл.", "Челябинская обл.", "Красноярский край"
    };

    private static final String[][] CATEGORIES = {
            // {category, okpdCode, methodPrefix}
            {"Строительство и ремонт",   "42.11", "Электронный аукцион"},
            {"Медицина и фарма",         "21.20", "Запрос котировок"},
            {"IT и связь",               "62.01", "Электронный аукцион"},
            {"Транспорт",                "49.41", "Электронный конкурс"},
            {"Канцтовары и поставки",    "17.23", "Запрос котировок"},
            {"Услуги клининга",          "81.21", "Электронный аукцион"},
            {"Охрана",                   "80.10", "Электронный конкурс"},
            {"Питание",                  "56.29", "Электронный аукцион"},
            {"Образование и обучение",   "85.42", "Электронный конкурс"},
            {"Оборудование",             "26.20", "Электронный аукцион"},
    };

    /** [заказчик, ИНН, законы (44, 223, оба)] */
    private static final String[][] CUSTOMERS = {
            {"ГБУЗ \"Городская поликлиника № 5\" г. Москвы", "7723123456", "44"},
            {"ФГБУ \"Российская детская клиническая больница\"", "7728123456", "44"},
            {"Министерство транспорта Российской Федерации", "7707011001", "44"},
            {"Администрация Нижегородского района", "5260100001", "44"},
            {"ФГАОУ ВО \"Высшая школа экономики\"", "7714030726", "44"},
            {"МКУ \"Управление капитального строительства\"", "5260200002", "44"},
            {"ОАО \"РЖД\"", "7708503727", "223"},
            {"ПАО \"Россети\"", "7728662669", "223"},
            {"АО \"Почта России\"", "7724490000", "223"},
            {"ГУП \"Мосводоканал\"", "7701984274", "223"},
            {"ПАО \"Аэрофлот\"", "7712040126", "223"},
            {"АО \"Российские космические системы\"", "7717033134", "223"},
            {"ФАУ \"РОСДОРНИИ\"", "7704035636", "44"},
            {"МБОУ \"Средняя школа № 23\"", "5260300003", "44"},
            {"ФКУ \"Управление автомобильной магистрали М-7\"", "5258012345", "44"},
    };

    /** Шаблоны названий закупок: [category_index, шаблон, мин.цена_тыс, макс.цена_тыс] */
    private static final Object[][] TITLE_TEMPLATES = {
            {0, "Капитальный ремонт фасада здания", 800, 15000},
            {0, "Текущий ремонт кровли учебного корпуса", 500, 5000},
            {0, "Реконструкция системы отопления", 1200, 8000},
            {0, "Ремонт автомобильной дороги общего пользования", 5000, 80000},
            {1, "Поставка лекарственных препаратов", 200, 3000},
            {1, "Поставка медицинского оборудования (УЗИ-аппарат)", 1500, 12000},
            {1, "Поставка одноразовых расходных материалов", 100, 2000},
            {2, "Разработка и сопровождение информационной системы", 2000, 25000},
            {2, "Поставка серверного оборудования", 1500, 18000},
            {2, "Услуги по технической поддержке инфраструктуры", 800, 6000},
            {3, "Транспортные услуги по перевозке сотрудников", 400, 3500},
            {3, "Услуги по перевозке грузов автомобильным транспортом", 600, 4500},
            {4, "Поставка канцелярских товаров и бумажной продукции", 80, 800},
            {4, "Поставка бумаги офисной формата А4", 50, 400},
            {5, "Услуги по уборке административных помещений", 300, 2500},
            {5, "Комплексная уборка зданий и прилегающей территории", 600, 4000},
            {6, "Услуги физической охраны объектов", 800, 6000},
            {7, "Организация горячего питания учащихся", 1500, 9000},
            {8, "Услуги по повышению квалификации сотрудников", 200, 1200},
            {9, "Поставка офисной мебели", 300, 2000},
            {9, "Поставка кондиционеров и климатического оборудования", 500, 4000},
    };

    @Override
    public List<Tender> fetchInitial() {
        List<Tender> tenders = new ArrayList<>();
        Random rnd = new Random(42); // фиксированный seed — стабильные данные

        for (int i = 0; i < 50; i++) {
            Object[] template = TITLE_TEMPLATES[rnd.nextInt(TITLE_TEMPLATES.length)];
            int catIdx = (int) template[0];
            String titleTemplate = (String) template[1];
            int minPrice = (int) template[2];
            int maxPrice = (int) template[3];

            String[] customer = CUSTOMERS[rnd.nextInt(CUSTOMERS.length)];
            String[] catInfo = CATEGORIES[catIdx];

            TenderLawType law = "44".equals(customer[2]) ? TenderLawType.FZ_44 : TenderLawType.FZ_223;
            TenderStatus status = pickStatus(rnd);

            LocalDateTime publishedAt = LocalDateTime.now().minusDays(rnd.nextInt(45));
            LocalDateTime deadline = publishedAt.plusDays(7 + rnd.nextInt(21));
            // Если статус "завершена", дедлайн в прошлом
            if (status == TenderStatus.COMPLETED || status == TenderStatus.CANCELLED) {
                deadline = LocalDateTime.now().minusDays(rnd.nextInt(30) + 1);
            }

            BigDecimal price = BigDecimal.valueOf((minPrice + rnd.nextInt(maxPrice - minPrice)) * 1000L);
            BigDecimal appSecurity = price.multiply(BigDecimal.valueOf(0.005 + rnd.nextDouble() * 0.025))
                    .setScale(0, java.math.RoundingMode.HALF_UP);
            BigDecimal contractSecurity = price.multiply(BigDecimal.valueOf(0.05 + rnd.nextDouble() * 0.25))
                    .setScale(0, java.math.RoundingMode.HALF_UP);

            String registryNumber = generateRegistryNumber(law, customer[1], i);

            String okpd = catInfo[1] + "." + (10 + rnd.nextInt(89));

            Tender t = Tender.builder()
                    .registryNumber(registryNumber)
                    .title(titleTemplate)
                    .description(generateDescription(titleTemplate, customer[0]))
                    .customerName(customer[0])
                    .customerInn(customer[1])
                    .lawType(law)
                    .procurementMethod(catInfo[2])
                    .initialPrice(price)
                    .currency("RUB")
                    .region(REGIONS[rnd.nextInt(REGIONS.length)])
                    .okpdCode(okpd)
                    .category(catInfo[0])
                    .publishedAt(publishedAt)
                    .submissionDeadline(deadline)
                    .auctionDate(deadline.toLocalDate().plusDays(3))
                    .applicationSecurity(appSecurity)
                    .contractSecurity(contractSecurity)
                    .mspOnly(law == TenderLawType.FZ_44 && rnd.nextDouble() < 0.4)
                    .status(status)
                    .sourceUrl("https://zakupki.gov.ru/epz/order/notice/ea44/view/common-info.html?regNumber=" + registryNumber)
                    .source("mock")
                    .build();

            tenders.add(t);
        }

        return tenders;
    }

    private TenderStatus pickStatus(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.55) return TenderStatus.PUBLISHED;
        if (r < 0.70) return TenderStatus.UNDER_REVIEW;
        if (r < 0.80) return TenderStatus.AUCTION;
        if (r < 0.95) return TenderStatus.COMPLETED;
        return TenderStatus.CANCELLED;
    }

    private String generateRegistryNumber(TenderLawType law, String inn, int seq) {
        // Формат как в ЕИС: 0173100012324000456 (19 цифр)
        String prefix = law == TenderLawType.FZ_44 ? "01" : "32";
        String innPart = inn.length() >= 4 ? inn.substring(0, 4) : "0000";
        return prefix + innPart + "01233" + String.format("%07d", 1000 + seq);
    }

    private String generateDescription(String title, String customer) {
        return String.format(
                "%s. Заказчик: %s. Закупка проводится в соответствии с действующим законодательством " +
                "Российской Федерации о закупках. Подробные требования к участникам, " +
                "техническое задание и проект контракта опубликованы в Единой информационной системе. " +
                "Подача заявок осуществляется в электронной форме через оператора электронной площадки.",
                title, customer
        );
    }
}
