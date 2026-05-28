#pragma once
#include "models.hpp"
#include <vector>
#include <string>
#include <random>
#include <ctime>
#include <sstream>
#include <iomanip>

class MockTenderProvider {
public:
    std::vector<Tender> generate() {
        std::mt19937 rng(42);
        std::vector<Tender> tenders;
        tenders.reserve(50);

        for (int i = 0; i < 50; ++i) {
            Tender t;
            t.id = 0;

            std::uniform_int_distribution<int> region_dist(0, regions_.size() - 1);
            std::uniform_int_distribution<int> category_dist(0, categories_.size() - 1);
            std::uniform_int_distribution<int> customer_dist(0, customers_.size() - 1);
            std::uniform_int_distribution<int> law_dist(0, 2);
            std::uniform_int_distribution<int> status_dist(0, 4);
            std::uniform_int_distribution<int> price_dist(100000, 50000000);
            std::uniform_int_distribution<int> days_pub_dist(1, 30);
            std::uniform_int_distribution<int> days_dead_dist(7, 60);
            std::uniform_int_distribution<int> method_dist(0, methods_.size() - 1);

            int cat_idx = category_dist(rng);
            int region_idx = region_dist(rng);
            int customer_idx = customer_dist(rng);

            std::ostringstream reg_num;
            reg_num << std::setfill('0') << std::setw(13) << (123456789012L + i) << "-24-" << std::setw(5) << (i + 1);
            t.registry_number = reg_num.str();

            t.title = titles_[cat_idx % titles_.size()];
            t.description = "Подробное описание закупки: " + t.title;
            t.customer_name = customers_[customer_idx];
            t.customer_inn = std::to_string(7700000000L + i * 11);

            int lt = law_dist(rng);
            t.law_type = (lt == 0) ? "FZ_44" : (lt == 1) ? "FZ_223" : "COMMERCIAL";
            t.procurement_method = methods_[method_dist(rng)];
            t.initial_price = static_cast<double>(price_dist(rng));
            t.currency = "RUB";
            t.region = regions_[region_idx];
            t.okpd_code = okpd_codes_[cat_idx % okpd_codes_.size()];
            t.category = categories_[cat_idx];

            auto now = std::time(nullptr);
            int pub_days_ago = days_pub_dist(rng);
            int dead_days_ahead = days_dead_dist(rng);

            auto pub_time = now - pub_days_ago * 86400;
            auto dead_time = now + dead_days_ahead * 86400;
            auto auction_time = dead_time + 3 * 86400;

            t.published_at = format_timestamp(pub_time);
            t.submission_deadline = format_timestamp(dead_time);
            t.auction_date = format_date(auction_time);

            double app_sec = t.initial_price.value_or(0) * 0.05;
            double con_sec = t.initial_price.value_or(0) * 0.10;
            t.application_security = app_sec;
            t.contract_security = con_sec;

            std::uniform_int_distribution<int> msp_dist(0, 3);
            t.msp_only = (msp_dist(rng) == 0);

            int st = status_dist(rng);
            const char* statuses[] = {"PUBLISHED", "UNDER_REVIEW", "AUCTION", "COMPLETED", "CANCELLED"};
            t.status = statuses[st];
            t.source_url = "https://zakupki.gov.ru/epz/order/notice/ea20/view/common-info.html?regNumber=" + t.registry_number;
            t.source = "MOCK";
            t.created_at = format_timestamp(now);

            tenders.push_back(std::move(t));
        }
        return tenders;
    }

private:
    std::string format_timestamp(std::time_t t) {
        char buf[64];
        std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", std::localtime(&t));
        return std::string(buf);
    }

    std::string format_date(std::time_t t) {
        char buf[32];
        std::strftime(buf, sizeof(buf), "%Y-%m-%d", std::localtime(&t));
        return std::string(buf);
    }

    std::vector<std::string> regions_ = {
        "Москва", "Санкт-Петербург", "Новосибирская область",
        "Свердловская область", "Краснодарский край",
        "Республика Татарстан", "Нижегородская область",
        "Самарская область", "Ростовская область", "Челябинская область"
    };

    std::vector<std::string> categories_ = {
        "Строительство", "Медицина", "IT и телекоммуникации",
        "Транспорт", "Образование", "Продукты питания",
        "Канцелярия", "Охрана", "Клининг", "Ремонт оборудования"
    };

    std::vector<std::string> customers_ = {
        "ГБУЗ \"Городская больница №3\"",
        "ФГБУ \"НИИ Здоровья\"",
        "МБУ \"Школа №42\"",
        "ГКУ \"Управление дорог\"",
        "АО \"РЖД\"",
        "ПАО \"Аэрофлот\"",
        "ФГУП \"Почта России\"",
        "МУП \"Водоканал\"",
        "ГБУ \"Парки Москвы\"",
        "ФГБОУ ВО \"МГУ\""
    };

    std::vector<std::string> titles_ = {
        "Капитальный ремонт здания административного корпуса",
        "Поставка медицинского оборудования для диагностики",
        "Разработка и внедрение информационной системы",
        "Оказание транспортных услуг по перевозке грузов",
        "Поставка учебной литературы и методических материалов",
        "Поставка продуктов питания для школьной столовой",
        "Поставка канцелярских товаров и расходных материалов",
        "Оказание услуг охраны объектов заказчика",
        "Комплексная уборка помещений и прилегающей территории",
        "Техническое обслуживание и ремонт оборудования"
    };

    std::vector<std::string> methods_ = {
        "Электронный аукцион",
        "Запрос котировок",
        "Конкурс",
        "Закупка у единственного поставщика",
        "Запрос предложений"
    };

    std::vector<std::string> okpd_codes_ = {
        "41.20", "32.50", "62.01", "49.41", "58.11",
        "10.89", "17.23", "80.10", "81.21", "33.12"
    };
};
