package com.example.demo;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.GetChatMemberResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class DemoApplication {

    private static final String BOT_TOKEN = "8550718454:AAHClYUEW6c6ev9gKi84LCYoprt1gyJgtM8";
    private static final String BOT_USERNAME = "@BravlStarsofficialBot";
    private static final String ADMIN_USERNAME = "@yunusovdiyorbek";

    private TelegramBot bot;
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final List<Akkaunt> akkaunts = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, PromoCode> promoCodes = new ConcurrentHashMap<>();
    private final AtomicInteger akkauntIdGenerator = new AtomicInteger(1);

    private List<String> mandatoryChannels = new ArrayList<>(List.of("@bravlstaruz"));

    private int dailyBonus = 100;
    private int referralBonus = 200;

    // Yangi Bust variantlari (rank ko'tarish bilan)
    private final List<BustOption> bustOptions = Arrays.asList(
            new BustOption("300 kubok + 3 ta kvest — 500 eliksir 🧪", 500),
            new BustOption("500 kubok + 5 ta kvest — 800 eliksir 🧪", 800),
            new BustOption("700 kubok + 7 ta kvest — 1000 eliksir 🧪", 1000),
            new BustOption("1000 kubok + 10 ta kvest — 1300 eliksir 🧪", 1300),
            new BustOption("1-25 rank ko'tarish — 350 eliksir 🧪", 350),
            new BustOption("1-30 rank ko'tarish — 400 eliksir 🧪", 400),
            new BustOption("1-35 rank ko'tarish — 450 eliksir 🧪", 450),
            new BustOption("1-40 rank ko'tarish — 500 eliksir 🧪", 500),
            new BustOption("1-50 rank ko'tarish — 700 eliksir 🧪", 700)
    );

    private final List<DonatOption> donatOptions = Arrays.asList(
            new DonatOption("30 gem", 2500),
            new DonatOption("80 gem", 7500),
            new DonatOption("170 gem", 15000),
            new DonatOption("360 gem", 30000)
    );

    private final List<PaymentOption> paymentOptions = Arrays.asList(
            new PaymentOption(1000, 15000),
            new PaymentOption(2000, 33000),
            new PaymentOption(3000, 40000),
            new PaymentOption(5000, 65000),
            new PaymentOption(10000, 120000)
    );

    private final List<PaymentCard> paymentCards = Collections.synchronizedList(new ArrayList<>());

    private final List<PendingPayment> pendingPayments = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger paymentIdGenerator = new AtomicInteger(1);

    private String completedTasksChannel = "@bravlstaruz";

    private final Map<String, String> faqTexts = new ConcurrentHashMap<>(Map.of(
            "uz", "Ko'p so'raladigan savollar:\n1. Bust qanday ishlaydi? ...\n2. Donat qanday? ...",
            "ru", "Часто задаваемые вопросы:\n1. Как работает буст? ...\n2. Как донат? ..."
    ));

    private static class User {
        String username;
        LocalDateTime joinDate = LocalDateTime.now();
        boolean isAdmin = false;
        int eliksir = 0;
        List<String> history = new ArrayList<>();
        List<InventoryItem> inventory = new ArrayList<>();
        LocalDate lastBonusClaim = LocalDate.now().minusDays(1);
        int referralCount = 0;
        int totalDonated = 0;
        Set<String> usedPromoCodes = new HashSet<>();
        boolean pendingPromo = false;
        boolean inAdminPanel = false;
        String awaitingInput = null;
        String tempData = null;
        int selectedElixir = 0;
        String language = null;
    }

    private record BustOption(String description, int price) {}
    private record DonatOption(String description, int price) {}
    private record PaymentOption(int elixir, int priceSom) {}

    private static class PromoCode {
        int amount;
        int usageLimit;
        int usedCount = 0;
    }

    private static class Akkaunt {
        int id;
        String description;
        String imageFileId;
        int price;
        String gmail;
        String password;
        boolean sold = false;
    }

    private static class InventoryItem {
        String id;
        String type;
        String description;
        LocalDateTime purchaseDate;
        boolean completed = false;
        String details;
        long userChatId;
    }

    private static class PendingPayment {
        int id;
        long userId;
        int elixir;
        int priceSom;
        String photoFileId;
    }

    private static class PaymentCard {
        String name;
        String number;
    }

    private final AtomicInteger inventoryIdGenerator = new AtomicInteger(1);

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @PostConstruct
    public void startBot() {
        bot = new TelegramBot(BOT_TOKEN);
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                try {
                    if (update.message() != null) {
                        handleMessage(update.message());
                    } else if (update.callbackQuery() != null) {
                        handleCallback(update.callbackQuery());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        System.out.println("Bot muvaffaqiyatli ishga tushdi! 🔥");
    }

    private void handleMessage(Message message) {
        long chatId = message.chat().id();
        String text = message.text() != null ? message.text().trim() : "";
        String username = message.from().username() != null ? "@" + message.from().username() : message.from().firstName();
        User user = getOrCreateUser(chatId, username);

        if (user.language == null && !text.startsWith("/start") && !text.equals("O'zbekcha 🇺🇿") && !text.equals("Русский 🇷🇺")) {
            sendLanguageSelection(chatId);
            return;
        }

        if (!checkSubscriptions(chatId)) {
            if (text.startsWith("/start")) {
                handleStartCommand(chatId, user, text);
            } else if (text.equals("O'zbekcha 🇺🇿") || text.equals("Русский 🇷🇺")) {
                setLanguage(chatId, user, text);
            } else {
                sendSubscriptionRequest(chatId, user.language);
            }
            return;
        }

        if (user.awaitingInput != null && user.awaitingInput.equals("payment_check")) {
            if (message.photo() != null) {
                String fileId = message.photo()[message.photo().length - 1].fileId();
                processPaymentCheck(chatId, user, fileId);
            } else {
                sendMessage(chatId, getText(user.language, "send_photo_check"), ParseMode.Markdown);
            }
            return;
        }

        if (user.awaitingInput != null && user.awaitingInput.equals("akkaunt_file") && user.isAdmin) {
            if (message.photo() != null) {
                String fileId = message.photo()[message.photo().length - 1].fileId();
                processAdminAkkauntWithPhoto(chatId, user, fileId);
            } else {
                sendMessage(chatId, getText(user.language, "send_photo"), ParseMode.Markdown);
            }
            return;
        }

        if (user.awaitingInput != null && user.isAdmin) {
            processAdminInput(chatId, text, user);
            return;
        }

        if (user.pendingPromo) {
            applyPromo(chatId, text, user);
            return;
        }

        if (user.inAdminPanel && user.isAdmin) {
            handleAdminPanelClick(text, chatId, user);
            return;
        }

        if (text.startsWith("/start")) {
            handleStartCommand(chatId, user, text);
        } else if (text.equals("O'zbekcha 🇺🇿") || text.equals("Русский 🇷🇺")) {
            setLanguage(chatId, user, text);
        } else {
            switch (text) {
                case "\uD83D\uDD79️ Serverlar" -> sendServerSelection(chatId, user.language);
                case "\uD83D\uDC64 Profil" -> sendProfile(chatId, user);
                case "\uD83D\uDCB0 Hisob to'ldirish" -> sendTopUpOptions(chatId, user.language);
                case "\uD83C\uDF81 Promokod" -> requestPromoCode(chatId, user);
                case "☎️ Qo'llab-quvvatlash" -> sendSupportMenu(chatId, user.language);
                case "⚙️ Admin Panel" -> {
                    if (user.isAdmin) {
                        user.inAdminPanel = true;
                        sendAdminPanel(chatId, user.language);
                    }
                }
                case "📦 Inventar" -> sendInventory(chatId, user);
                default -> {
                    sendMessage(chatId, getText(user.language, "unknown_command"), ParseMode.Markdown);
                    sendMainMenu(chatId, getText(user.language, "main_menu"), user.language);
                }
            }
        }
    }

    private void setLanguage(long chatId, User user, String lang) {
        user.language = lang.equals("O'zbekcha 🇺🇿") ? "uz" : "ru";
        sendSubscriptionRequest(chatId, user.language);
    }

    private void sendLanguageSelection(long chatId) {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(
                new KeyboardButton("O'zbekcha 🇺🇿"),
                new KeyboardButton("Русский 🇷🇺")
        ).resizeKeyboard(true).oneTimeKeyboard(true);
        sendMessage(chatId, "🌐 Tilni tanlang / Выберите язык: 🌐", keyboard);
    }

    private void handleStartCommand(long chatId, User user, String text) {
        if (user.language == null) {
            sendLanguageSelection(chatId);
            return;
        }
        String[] parts = text.split(" ");
        if (parts.length > 1 && parts[1].startsWith("ref_")) {
            try {
                long referrerId = Long.parseLong(parts[1].substring(4));
                if (referrerId != chatId && users.containsKey(referrerId)) {
                    User referrer = users.get(referrerId);
                    referrer.referralCount++;
                    referrer.eliksir += referralBonus;
                    referrer.history.add(getText(referrer.language, "new_referral") + referralBonus + " eliksir 🧪");
                    sendMessage(referrerId, getText(referrer.language, "new_referral_notify") + referralBonus + "\nBalans: *" + referrer.eliksir + "* 🧪", ParseMode.Markdown);
                }
            } catch (Exception ignored) {}
        }
        if (checkSubscriptions(chatId)) {
            String welcomeMessage = getText(user.language, "welcome_message")
                    .replace("{username}", user.username)
                    .replace("{eliksir}", String.valueOf(user.eliksir));
            sendMainMenu(chatId, welcomeMessage, user.language);
        } else {
            sendSubscriptionRequest(chatId, user.language);
        }
    }

    private void handleCallback(CallbackQuery callback) {
        long chatId = callback.message().chat().id();
        String data = callback.data();
        User user = users.get(chatId);
        if (user == null) return;

        if (!checkSubscriptions(chatId) && !"check_sub".equals(data)) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text(getText(user.language, "subscribe_first")).showAlert(true));
            sendSubscriptionRequest(chatId, user.language);
            return;
        }

        bot.execute(new AnswerCallbackQuery(callback.id()));

        switch (data) {
            case "check_sub" -> {
                if (checkSubscriptions(chatId)) {
                    sendMainMenu(chatId, getText(user.language, "welcome_back"), user.language);
                } else {
                    sendMessage(chatId, getText(user.language, "subscribe_all"), ParseMode.Markdown);
                }
            }
            case "bust" -> sendBustPrices(chatId, user.language);
            case "donat" -> sendDonatOptions(chatId, user.language);
            case "akkaunt" -> sendAkkauntList(chatId, user.language);
            case "referal" -> sendMessage(chatId, getText(user.language, "referal_link") + "https://t.me/" + BOT_USERNAME.substring(1) + "?start=ref_" + chatId);
            case "kunlik_bonus" -> claimDailyBonus(chatId, user);
            case "tolov" -> sendPaymentOptions(chatId, user.language);
            case "buy_bust" -> sendBustOptions(chatId, user.language);
            case "cancel" -> sendMessage(chatId, getText(user.language, "canceled"), ParseMode.Markdown);
            case "admin_exit" -> {
                user.inAdminPanel = false;
                user.awaitingInput = null;
                sendMainMenu(chatId, getText(user.language, "admin_exit_message"), user.language);
            }
            case "support_faq" -> sendMessage(chatId, faqTexts.get(user.language), ParseMode.Markdown);
            case "support_admin" -> sendMessage(chatId, getText(user.language, "contact_admin") + ADMIN_USERNAME, ParseMode.Markdown);
            default -> {
                if (data.startsWith("bust_")) {
                    int index = Integer.parseInt(data.split("_")[1]) - 1;
                    sendBustConfirmation(chatId, index, user.language);
                } else if (data.startsWith("confirm_bust_")) {
                    int index = Integer.parseInt(data.split("_")[2]) - 1;
                    purchaseItem(chatId, user, "bust", bustOptions.get(index).description, bustOptions.get(index).price);
                } else if (data.startsWith("donat_")) {
                    int index = Integer.parseInt(data.split("_")[1]) - 1;
                    sendDonatConfirmation(chatId, index, user.language);
                } else if (data.startsWith("confirm_donat_")) {
                    int index = Integer.parseInt(data.split("_")[2]) - 1;
                    purchaseItem(chatId, user, "donat", donatOptions.get(index).description, donatOptions.get(index).price);
                } else if (data.startsWith("buy_akkaunt_")) {
                    int id = Integer.parseInt(data.split("_")[2]);
                    buyAkkaunt(chatId, user, id);
                } else if (data.startsWith("complete_task_") && user.isAdmin) {
                    String itemId = data.substring(14);
                    completeTask(itemId, chatId, user.language);
                } else if (data.startsWith("admin_")) {
                    handleAdminCallback(chatId, user, data);
                } else if (data.startsWith("select_payment_")) {
                    int index = Integer.parseInt(data.split("_")[2]);
                    confirmPayment(chatId, user, index, user.language);
                } else if (data.startsWith("confirm_payment")) {
                    requestPaymentCheck(chatId, user, user.language);
                } else if (data.startsWith("approve_payment_") && user.isAdmin) {
                    int paymentId = Integer.parseInt(data.split("_")[2]);
                    approvePayment(chatId, paymentId);
                } else if (data.startsWith("admin_elixir_add")) {
                    user.awaitingInput = "elixir_amount_add";
                    sendMessage(chatId, getText(user.language, "enter_add_amount"), ParseMode.Markdown);
                } else if (data.startsWith("admin_elixir_subtract")) {
                    user.awaitingInput = "elixir_amount_subtract";
                    sendMessage(chatId, getText(user.language, "enter_subtract_amount"), ParseMode.Markdown);
                } else if (data.startsWith("admin_elixir_view")) {
                    User target = findUserByUsername(user.tempData);
                    if (target != null) {
                        sendMessage(chatId, getText(user.language, "elixir_view") + target.eliksir + " 🧪", ParseMode.Markdown);
                    } else {
                        sendMessage(chatId, getText(user.language, "user_not_found"), ParseMode.Markdown);
                    }
                    user.awaitingInput = null;
                    user.tempData = null;
                }
            }
        }
    }

    private void sendSupportMenu(long chatId, String lang) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "faq_button")).callbackData("support_faq"),
                        new InlineKeyboardButton(getText(lang, "admin_button")).callbackData("support_admin"));
        sendMessage(chatId, getText(lang, "support_menu"), keyboard, ParseMode.Markdown);
    }

    private void sendServerSelection(long chatId, String lang) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton("🏆 Bust").callbackData("bust"),
                        new InlineKeyboardButton("💎 Donat").callbackData("donat"))
                .addRow(new InlineKeyboardButton("🔑 Akkaunt").callbackData("akkaunt"));
        sendMessage(chatId, getText(lang, "server_services"), keyboard, ParseMode.Markdown);
    }

    private void sendBustPrices(long chatId, String lang) {
        String message = "🛡💥 🏆 *BUST XIZMATI* 🌟 💥🛡\n\n" +
                "🔥 *Kubok va Kvest Mukofotlari* 🔥\n" +
                "🏅 300 🏆 + 3 🎯 → 500 🧪\n" +
                "🏅 500 🏆 + 5 🎯 → 800 🧪\n" +
                "🏅 700 🏆 + 7 🎯 → 1000 🧪\n" +
                "🏅 1000 🏆 + 10 🎯 → 1300 🧪\n\n" +
                "✅ *Rank Ko’tarish*\n" +
                "⭐️ 1 → 25 → 350 🧪\n" +
                "⭐️ 1 → 30 → 400 🧪\n" +
                "⭐️ 1 → 35 → 450 🧪\n" +
                "⭐️ 1 → 40 → 500 🧪\n" +
                "⭐️ 1 → 50 → 700 🧪\n\n" +
                "🔐 *Supercell ID orqali tez va xavfsiz ishlaymiz!* 🔒";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton(getText(lang, "buy")).callbackData("buy_bust")
        );
        sendMessage(chatId, message, keyboard, ParseMode.Markdown);
    }

    private void sendBustOptions(long chatId, String lang) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        for (int i = 0; i < bustOptions.size(); i++) {
            keyboard.addRow(new InlineKeyboardButton(bustOptions.get(i).description).callbackData("bust_" + (i + 1)));
        }
        sendMessage(chatId, getText(lang, "select_bust"), keyboard, ParseMode.Markdown);
    }

    private void sendBustConfirmation(long chatId, int index, String lang) {
        BustOption opt = bustOptions.get(index);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "yes")).callbackData("confirm_bust_" + (index + 1)),
                        new InlineKeyboardButton(getText(lang, "no")).callbackData("cancel"));
        sendMessage(chatId, getText(lang, "confirm_buy") + opt.description + "\n" + getText(lang, "price") + opt.price + " eliksir 🧪", keyboard, ParseMode.Markdown);
    }

    private void sendDonatOptions(long chatId, String lang) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        for (int i = 0; i < donatOptions.size(); i++) {
            DonatOption opt = donatOptions.get(i);
            keyboard.addRow(new InlineKeyboardButton(opt.description + " — " + opt.price + " eliksir 🧪").callbackData("donat_" + (i + 1)));
        }
        sendMessage(chatId, getText(lang, "select_donat"), keyboard, ParseMode.Markdown);
    }

    private void sendDonatConfirmation(long chatId, int index, String lang) {
        DonatOption opt = donatOptions.get(index);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "yes")).callbackData("confirm_donat_" + (index + 1)),
                        new InlineKeyboardButton(getText(lang, "no")).callbackData("cancel"));
        sendMessage(chatId, getText(lang, "confirm_buy") + opt.description + "\n" + getText(lang, "price") + opt.price + " eliksir 🧪", keyboard, ParseMode.Markdown);
    }

    private void sendAkkauntList(long chatId, String lang) {
        if (akkaunts.isEmpty()) {
            sendMessage(chatId, getText(lang, "no_akkaunts"), ParseMode.Markdown);
            return;
        }
        synchronized (akkaunts) {
            for (Akkaunt acc : akkaunts) {
                if (!acc.sold) {
                    String caption = "*" + acc.description + "* 🌟\n" + getText(lang, "price") + "*" + acc.price + "* eliksir 🧪";
                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                            new InlineKeyboardButton(getText(lang, "buy")).callbackData("buy_akkaunt_" + acc.id)
                    );
                    if (acc.imageFileId != null) {
                        bot.execute(new SendPhoto(chatId, acc.imageFileId).caption(caption).parseMode(ParseMode.Markdown).replyMarkup(keyboard));
                    } else {
                        sendMessage(chatId, caption, keyboard, ParseMode.Markdown);
                    }
                }
            }
        }
    }

    private void buyAkkaunt(long chatId, User user, int id) {
        Akkaunt acc = akkaunts.stream().filter(a -> a.id == id && !a.sold).findFirst().orElse(null);
        if (acc == null) {
            sendMessage(chatId, getText(user.language, "akkaunt_sold_or_not_found"), ParseMode.Markdown);
            return;
        }
        if (user.eliksir >= acc.price) {
            user.eliksir -= acc.price;
            acc.sold = true;
            InventoryItem item = new InventoryItem();
            item.id = "item_" + inventoryIdGenerator.getAndIncrement();
            item.type = "akkaunt";
            item.description = acc.description;
            item.purchaseDate = LocalDateTime.now();
            item.completed = true;
            item.userChatId = chatId;
            item.details = "Gmail: " + acc.gmail + "\nParol: " + acc.password;
            user.inventory.add(item);
            user.history.add(getText(user.language, "akkaunt_bought") + acc.description + " (-" + acc.price + ") 🧪");
            sendMessage(chatId, getText(user.language, "bought_success") + "\n\nGmail: `" + acc.gmail + "`\nParol: `" + acc.password + "`\n\n" + getText(user.language, "added_to_inventory"), ParseMode.Markdown);
            long adminId = getAdminChatId();
            if (adminId != 0) {
                sendMessage(adminId, getText("uz", "akkaunt_sold_notify") + user.username + "\nAkkaunt: " + acc.description, ParseMode.Markdown);
            }
        } else {
            sendMessage(chatId, getText(user.language, "not_enough_eliksir"), ParseMode.Markdown);
        }
    }

    private void sendProfile(long chatId, User user) {
        long days = ChronoUnit.DAYS.between(user.joinDate.toLocalDate(), LocalDate.now());
        StringBuilder sb = new StringBuilder(getText(user.language, "profile") + "\n\n")
                .append(getText(user.language, "user") + user.username + "\n")
                .append(getText(user.language, "balance") + "*" + user.eliksir + "* eliksir 🧪\n")
                .append(getText(user.language, "referrals") + "*" + user.referralCount + "* ta (har biri +" + referralBonus + " 🧪)\n")
                .append(getText(user.language, "total_donat") + "*" + user.totalDonated + "* so'm 💰\n")
                .append(getText(user.language, "days_in_bot") + "*" + days + "* kun\n");

        sendMessage(chatId, sb.toString(), ParseMode.Markdown);
    }

    private void sendTopUpOptions(long chatId, String lang) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "referal")).callbackData("referal"),
                        new InlineKeyboardButton(getText(lang, "daily_bonus")).callbackData("kunlik_bonus"))
                .addRow(new InlineKeyboardButton(getText(lang, "payment_card")).callbackData("tolov"));
        sendMessage(chatId, getText(lang, "top_up"), keyboard, ParseMode.Markdown);
    }

    private void claimDailyBonus(long chatId, User user) {
        LocalDate today = LocalDate.now();
        if (user.lastBonusClaim.isBefore(today)) {
            user.eliksir += dailyBonus;
            user.lastBonusClaim = today;
            user.history.add(getText(user.language, "daily_bonus_claimed") + "+" + dailyBonus + " eliksir 🧪");
            sendMessage(chatId, getText(user.language, "bonus_claimed") + "+" + dailyBonus + " eliksir\nBalans: *" + user.eliksir + "* 🧪", ParseMode.Markdown);
        } else {
            sendMessage(chatId, getText(user.language, "already_claimed_today"), ParseMode.Markdown);
        }
    }

    private void requestPromoCode(long chatId, User user) {
        user.pendingPromo = true;
        sendMessage(chatId, getText(user.language, "enter_promo"), ParseMode.Markdown);
    }

    private void applyPromo(long chatId, String code, User user) {
        String upperCode = code.toUpperCase();
        PromoCode pc = promoCodes.get(upperCode);
        if (pc != null) {
            if (user.usedPromoCodes.contains(upperCode)) {
                sendMessage(chatId, getText(user.language, "promo_already_used"), ParseMode.Markdown);
            } else if (pc.usageLimit == 0 || pc.usedCount < pc.usageLimit) {
                user.eliksir += pc.amount;
                pc.usedCount++;
                user.usedPromoCodes.add(upperCode);
                if (pc.usageLimit != 0 && pc.usedCount >= pc.usageLimit) {
                    promoCodes.remove(upperCode);
                }
                user.history.add(getText(user.language, "promo_applied") + "+" + pc.amount + " eliksir 🧪");
                sendMessage(chatId, getText(user.language, "promo_success") + "+" + pc.amount + " eliksir\nBalans: *" + user.eliksir + "* 🧪", ParseMode.Markdown);
            } else {
                sendMessage(chatId, getText(user.language, "promo_expired"), ParseMode.Markdown);
            }
        } else {
            sendMessage(chatId, getText(user.language, "invalid_promo"), ParseMode.Markdown);
        }
        user.pendingPromo = false;
    }

    private void sendPaymentOptions(long chatId, String lang) {
        if (paymentCards.isEmpty()) {
            sendMessage(chatId, getText(lang, "no_payment_method"), ParseMode.Markdown);
            return;
        }
        StringBuilder sb = new StringBuilder(getText(lang, "payment_options") + "\n\n");
        for (int i = 0; i < paymentOptions.size(); i++) {
            PaymentOption opt = paymentOptions.get(i);
            sb.append((i + 1)).append(". ").append(opt.elixir).append(" eliksir 🧪 — ").append(opt.priceSom).append(" so'm 💰\n");
        }
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        for (int i = 0; i < paymentOptions.size(); i += 2) {
            InlineKeyboardButton btn1 = new InlineKeyboardButton(paymentOptions.get(i).elixir + " eliksir 🧪 (" + paymentOptions.get(i).priceSom + " so'm)").callbackData("select_payment_" + i);
            if (i + 1 < paymentOptions.size()) {
                InlineKeyboardButton btn2 = new InlineKeyboardButton(paymentOptions.get(i + 1).elixir + " eliksir 🧪 (" + paymentOptions.get(i + 1).priceSom + " so'm)").callbackData("select_payment_" + (i + 1));
                keyboard.addRow(btn1, btn2);
            } else {
                keyboard.addRow(btn1);
            }
        }
        keyboard.addRow(new InlineKeyboardButton(getText(lang, "back")).callbackData("cancel"));
        sendMessage(chatId, sb.toString(), keyboard, ParseMode.Markdown);
    }

    private void confirmPayment(long chatId, User user, int index, String lang) {
        PaymentOption opt = paymentOptions.get(index);
        user.selectedElixir = opt.elixir;
        StringBuilder message = new StringBuilder(getText(lang, "payment_details") + "\n\n" + getText(lang, "selected") + opt.elixir + " eliksir 🧪\n" + getText(lang, "amount") + opt.priceSom + " so'm 💰\n\n" + getText(lang, "cards") + "\n");
        for (PaymentCard card : paymentCards) {
            message.append(card.name).append(": `").append(card.number).append("`\n");
        }
        message.append("\n" + getText(lang, "send_check_photo"));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "confirm_and_pay")).callbackData("confirm_payment"),
                        new InlineKeyboardButton(getText(lang, "back")).callbackData("cancel"));
        sendMessage(chatId, message.toString(), keyboard, ParseMode.Markdown);
    }

    private void requestPaymentCheck(long chatId, User user, String lang) {
        user.awaitingInput = "payment_check";
        sendMessage(chatId, getText(lang, "send_check_photo_request"), ParseMode.Markdown);
    }

    private void processPaymentCheck(long chatId, User user, String fileId) {
        PaymentOption opt = paymentOptions.stream().filter(p -> p.elixir == user.selectedElixir).findFirst().orElse(null);
        if (opt == null) {
            sendMessage(chatId, getText(user.language, "error_option_not_found"), ParseMode.Markdown);
            return;
        }
        PendingPayment payment = new PendingPayment();
        payment.id = paymentIdGenerator.getAndIncrement();
        payment.userId = chatId;
        payment.elixir = opt.elixir;
        payment.priceSom = opt.priceSom;
        payment.photoFileId = fileId;
        pendingPayments.add(payment);
        sendMessage(chatId, getText(user.language, "check_accepted"), ParseMode.Markdown);
        user.awaitingInput = null;
        user.selectedElixir = 0;
        long adminId = getAdminChatId();
        if (adminId != 0) {
            sendMessage(adminId, getText("uz", "new_payment_check") + user.username + "\nMiqdor: " + opt.elixir + " eliksir 🧪 (" + opt.priceSom + " so'm)", ParseMode.Markdown);
        }
    }

    private void approvePayment(long adminChatId, int paymentId) {
        PendingPayment payment = pendingPayments.stream().filter(p -> p.id == paymentId).findFirst().orElse(null);
        if (payment == null) {
            sendMessage(adminChatId, "🛑 To'lov topilmadi! 🚫", ParseMode.Markdown);
            return;
        }
        User user = users.get(payment.userId);
        if (user != null) {
            user.eliksir += payment.elixir;
            user.totalDonated += payment.priceSom;
            user.history.add(getText(user.language, "donat_added") + "+" + payment.elixir + " eliksir 🧪 (" + payment.priceSom + " so'm) 💰");
            sendMessage(payment.userId, getText(user.language, "payment_approved") + "+" + payment.elixir + " eliksir 🧪 qo'shildi!\nBalans: *" + user.eliksir + "* 🧪", ParseMode.Markdown);
        }
        pendingPayments.remove(payment);
        sendMessage(adminChatId, getText("uz", "payment_approved_success"), ParseMode.Markdown);
        sendPendingPayments(adminChatId);
    }

    private void sendPendingPayments(long chatId) {
        if (pendingPayments.isEmpty()) {
            sendMessage(chatId, "📥 Hozircha yangi to'lovlar yo'q! ⏳", ParseMode.Markdown);
            return;
        }
        synchronized (pendingPayments) {
            for (PendingPayment p : pendingPayments) {
                User u = users.get(p.userId);
                String caption = "💰 *To'lov cheki* 🌟\n\n" +
                        "Foydalanuvchi: " + (u != null ? u.username : "Noma'lum") + "\n" +
                        "Miqdor: " + p.elixir + " eliksir 🧪\n" +
                        "Summa: " + p.priceSom + " so'm 💰";
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                        new InlineKeyboardButton("✅ Tasdiqlash").callbackData("approve_payment_" + p.id)
                );
                bot.execute(new SendPhoto(chatId, p.photoFileId).caption(caption).parseMode(ParseMode.Markdown).replyMarkup(keyboard));
            }
        }
    }

    private void sendAdminPanel(long chatId, String lang) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton("🔗 Majburiy kanallar").callbackData("admin_manage_channel"),
                        new InlineKeyboardButton("🎁 Kunlik bonus").callbackData("admin_manage_bonus"))
                .addRow(new InlineKeyboardButton("🔑 Promokodlar").callbackData("admin_manage_promo"),
                        new InlineKeyboardButton("🔓 Akkauntlar").callbackData("admin_manage_akkaunt"))
                .addRow(new InlineKeyboardButton("📋 Vazifalar").callbackData("admin_manage_tasks"),
                        new InlineKeyboardButton("💳 Kartalar").callbackData("admin_manage_cards"))
                .addRow(new InlineKeyboardButton("📥 To'lovlar").callbackData("admin_manage_payments"),
                        new InlineKeyboardButton("📊 Statistika").callbackData("admin_manage_stats"))
                .addRow(new InlineKeyboardButton("📢 Xabar yuborish").callbackData("admin_manage_broadcast"),
                        new InlineKeyboardButton("🧪 Eliksir boshqarish").callbackData("admin_manage_elixir"))
                .addRow(new InlineKeyboardButton("👥 Referal bonus").callbackData("admin_manage_referral_bonus"),
                        new InlineKeyboardButton("🔔 Bajarilgan vazifalar kanali").callbackData("admin_manage_completed_channel"))
                .addRow(new InlineKeyboardButton("🚪 Chiqish").callbackData("admin_exit"));
        sendMessage(chatId, getText(lang, "admin_panel"), keyboard, ParseMode.Markdown);
    }

    private void handleAdminPanelClick(String text, long chatId, User user) {
        sendMessage(chatId, getText(user.language, "admin_use_buttons"), ParseMode.Markdown);
    }

    private void handleAdminCallback(long chatId, User user, String data) {
        String[] parts = data.split("_");
        String action = parts[1];
        String type = parts.length > 2 ? parts[2] : "";
        if (action.equals("manage")) {
            if (type.equals("channel")) showCurrentChannels(chatId, user.language);
            else if (type.equals("promo")) showCurrentPromocodes(chatId, user.language);
            else if (type.equals("akkaunt")) showCurrentAkkaunts(chatId, user.language);
            else if (type.equals("tasks")) sendPendingTasks(chatId, user.language);
            else if (type.equals("stats")) sendStatistics(chatId, user.language);
            else if (type.equals("bonus")) {
                user.awaitingInput = "bonus";
                sendMessage(chatId, getText(user.language, "enter_new_bonus"), ParseMode.Markdown);
            } else if (type.equals("cards")) showCurrentCards(chatId, user.language);
            else if (type.equals("payments")) sendPendingPayments(chatId);
            else if (type.equals("broadcast")) {
                user.awaitingInput = "broadcast_type";
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                        .addRow(new InlineKeyboardButton("Hammaga").callbackData("admin_broadcast_all"),
                                new InlineKeyboardButton("Bitta foydalanuvchiga").callbackData("admin_broadcast_single"));
                sendMessage(chatId, getText(user.language, "broadcast_type_prompt"), keyboard);
            } else if (type.equals("elixir")) {
                user.awaitingInput = "elixir_username";
                sendMessage(chatId, getText(user.language, "enter_elixir_username"), ParseMode.Markdown);
            } else if (type.equals("referral")) {
                user.awaitingInput = "referral_bonus";
                sendMessage(chatId, getText(user.language, "enter_new_referral_bonus"), ParseMode.Markdown);
            } else if (type.equals("completed")) {
                user.awaitingInput = "completed_channel";
                sendMessage(chatId, getText(user.language, "enter_completed_channel"), ParseMode.Markdown);
            }
        } else if (action.equals("add")) {
            user.awaitingInput = "add_" + type;
            if (type.equals("channel")) sendMessage(chatId, getText(user.language, "add_channel_prompt"), ParseMode.Markdown);
            else if (type.equals("promo")) sendMessage(chatId, getText(user.language, "add_promo_prompt"), ParseMode.Markdown);
            else if (type.equals("akkaunt")) {
                user.awaitingInput = "akkaunt";
                user.tempData = "";
                sendMessage(chatId, getText(user.language, "add_akkaunt_prompt"), ParseMode.Markdown);
            } else if (type.equals("card")) {
                user.awaitingInput = "add_card_name";
                sendMessage(chatId, getText(user.language, "enter_new_card_name"), ParseMode.Markdown);
            }
        } else if (action.equals("remove")) {
            listForRemove(chatId, type, user.language);
        } else if (action.equals("rem")) {
            String value = String.join("_", Arrays.copyOfRange(parts, 3, parts.length));
            if (type.equals("channel")) {
                int index = Integer.parseInt(value);
                if (index >= 0 && index < mandatoryChannels.size()) {
                    String removed = mandatoryChannels.remove(index);
                    sendMessage(chatId, getText(user.language, "removed") + removed);
                }
                showCurrentChannels(chatId, user.language);
            } else if (type.equals("promo")) {
                if (promoCodes.remove(value) != null) sendMessage(chatId, getText(user.language, "promo_removed") + value);
                showCurrentPromocodes(chatId, user.language);
            } else if (type.equals("akkaunt")) {
                int id = Integer.parseInt(value);
                akkaunts.removeIf(a -> a.id == id);
                sendMessage(chatId, getText(user.language, "akkaunt_removed") + id);
                showCurrentAkkaunts(chatId, user.language);
            } else if (type.equals("card")) {
                int index = Integer.parseInt(value);
                if (index >= 0 && index < paymentCards.size()) {
                    paymentCards.remove(index);
                    sendMessage(chatId, getText(user.language, "card_removed"));
                }
                showCurrentCards(chatId, user.language);
            }
        } else if (action.equals("broadcast")) {
            if (type.equals("all")) {
                user.awaitingInput = "broadcast_all";
                sendMessage(chatId, getText(user.language, "enter_broadcast_all"), ParseMode.Markdown);
            } else if (type.equals("single")) {
                user.awaitingInput = "broadcast_username";
                sendMessage(chatId, getText(user.language, "enter_broadcast_username"), ParseMode.Markdown);
            }
        } else if (action.equals("back")) {
            sendAdminPanel(chatId, user.language);
        }
    }

    private void showCurrentChannels(long chatId, String lang) {
        StringBuilder sb = new StringBuilder(getText(lang, "mandatory_channels") + "\n\n");
        if (mandatoryChannels.isEmpty()) sb.append(getText(lang, "none") + "\n");
        else for (int i = 0; i < mandatoryChannels.size(); i++) sb.append((i+1)).append(". ").append(mandatoryChannels.get(i)).append("\n");
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "add")).callbackData("admin_add_channel"),
                        new InlineKeyboardButton(getText(lang, "remove")).callbackData("admin_remove_channel"))
                .addRow(new InlineKeyboardButton(getText(lang, "back")).callbackData("admin_back_manage"));
        sendMessage(chatId, sb.toString(), keyboard, ParseMode.Markdown);
    }

    private void showCurrentPromocodes(long chatId, String lang) {
        StringBuilder sb = new StringBuilder(getText(lang, "promocodes") + "\n\n");
        if (promoCodes.isEmpty()) sb.append(getText(lang, "none") + "\n");
        else for (Map.Entry<String, PromoCode> e : promoCodes.entrySet()) {
            String rem = e.getValue().usageLimit == 0 ? getText(lang, "unlimited") : (e.getValue().usageLimit - e.getValue().usedCount) + " " + getText(lang, "remaining");
            sb.append(e.getKey()).append(" → ").append(e.getValue().amount).append(" eliksir (" + rem + ")\n");
        }
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "add")).callbackData("admin_add_promo"),
                        new InlineKeyboardButton(getText(lang, "remove")).callbackData("admin_remove_promo"))
                .addRow(new InlineKeyboardButton(getText(lang, "back")).callbackData("admin_back_manage"));
        sendMessage(chatId, sb.toString(), keyboard, ParseMode.Markdown);
    }

    private void showCurrentAkkaunts(long chatId, String lang) {
        StringBuilder sb = new StringBuilder(getText(lang, "akkaunts") + "\n\n");
        if (akkaunts.isEmpty()) sb.append(getText(lang, "none") + "\n");
        else for (Akkaunt a : akkaunts) if (!a.sold) sb.append("ID ").append(a.id).append(" - ").append(a.description).append(" (").append(a.price).append(" eliksir 🧪)\n");
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "add")).callbackData("admin_add_akkaunt"),
                        new InlineKeyboardButton(getText(lang, "remove")).callbackData("admin_remove_akkaunt"))
                .addRow(new InlineKeyboardButton(getText(lang, "back")).callbackData("admin_back_manage"));
        sendMessage(chatId, sb.toString(), keyboard, ParseMode.Markdown);
    }

    private void showCurrentCards(long chatId, String lang) {
        StringBuilder message = new StringBuilder(getText(lang, "current_cards") + "\n\n");
        if (paymentCards.isEmpty()) {
            message.append(getText(lang, "none") + "\n");
        } else {
            for (int i = 0; i < paymentCards.size(); i++) {
                PaymentCard card = paymentCards.get(i);
                message.append((i + 1)).append(". ").append(card.name).append(": ").append(card.number).append("\n");
            }
        }
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                .addRow(new InlineKeyboardButton(getText(lang, "add")).callbackData("admin_add_card"),
                        new InlineKeyboardButton(getText(lang, "remove")).callbackData("admin_remove_card"))
                .addRow(new InlineKeyboardButton(getText(lang, "back")).callbackData("admin_back_manage"));
        sendMessage(chatId, message.toString(), keyboard, ParseMode.Markdown);
    }

    private void sendPendingTasks(long chatId, String lang) {
        List<InventoryItem> pendingItems = new ArrayList<>();
        for (User u : users.values()) {
            for (InventoryItem item : u.inventory) {
                if (!item.completed && !item.type.equals("akkaunt")) pendingItems.add(item);
            }
        }
        if (pendingItems.isEmpty()) {
            sendMessage(chatId, getText(lang, "no_pending_tasks"), ParseMode.Markdown);
            return;
        }
        StringBuilder sb = new StringBuilder(getText(lang, "pending_tasks") + "\n\n");
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm");
        for (InventoryItem item : pendingItems) {
            sb.append("🔹 *").append(item.description).append("*\n");
            sb.append(" 👤 ").append(users.get(item.userChatId).username).append("\n");
            sb.append(" 📅 ").append(item.purchaseDate.format(formatter)).append("\n\n");
            keyboard.addRow(new InlineKeyboardButton(getText(lang, "completed")).callbackData("complete_task_" + item.id));
        }
        sendMessage(chatId, sb.toString(), keyboard, ParseMode.Markdown);
    }

    private void sendStatistics(long chatId, String lang) {
        int totalUsers = users.size();
        int totalChannels = mandatoryChannels.size();
        int totalPromocodes = promoCodes.size();
        long totalOrders = users.values().stream().flatMap(u -> u.inventory.stream()).count();
        long completedOrders = users.values().stream().flatMap(u -> u.inventory.stream()).filter(i -> i.completed).count();
        long pendingOrders = totalOrders - completedOrders;
        List<Map.Entry<Long, User>> topUsers = users.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().eliksir, e1.getValue().eliksir))
                .limit(5)
                .toList();
        StringBuilder sb = new StringBuilder(getText(lang, "statistics") + "\n\n");
        sb.append(getText(lang, "total_users") + totalUsers).append("\n");
        sb.append(getText(lang, "total_channels") + totalChannels).append("\n");
        sb.append(getText(lang, "total_promocodes") + totalPromocodes).append("\n");
        sb.append(getText(lang, "total_orders") + totalOrders).append("\n");
        sb.append(" " + getText(lang, "completed_orders") + completedOrders).append("\n");
        sb.append(" " + getText(lang, "pending_orders") + pendingOrders).append("\n\n");
        sb.append(getText(lang, "top_5") + "\n");
        for (int i = 0; i < topUsers.size(); i++) {
            User u = topUsers.get(i).getValue();
            sb.append((i+1)).append(". ").append(u.username).append(" — ").append(u.eliksir).append(" eliksir 🧪\n");
        }
        sendMessage(chatId, sb.toString(), ParseMode.Markdown);
    }

    private void listForRemove(long chatId, String type, String lang) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        if (type.equals("channel")) {
            for (int i = 0; i < mandatoryChannels.size(); i += 2) {
                InlineKeyboardButton btn1 = new InlineKeyboardButton("❌ " + mandatoryChannels.get(i)).callbackData("admin_rem_channel_" + i);
                if (i + 1 < mandatoryChannels.size()) {
                    InlineKeyboardButton btn2 = new InlineKeyboardButton("❌ " + mandatoryChannels.get(i + 1)).callbackData("admin_rem_channel_" + (i + 1));
                    keyboard.addRow(btn1, btn2);
                } else {
                    keyboard.addRow(btn1);
                }
            }
        } else if (type.equals("promo")) {
            List<String> codes = new ArrayList<>(promoCodes.keySet());
            for (int i = 0; i < codes.size(); i += 2) {
                InlineKeyboardButton btn1 = new InlineKeyboardButton("❌ " + codes.get(i)).callbackData("admin_rem_promo_" + codes.get(i));
                if (i + 1 < codes.size()) {
                    InlineKeyboardButton btn2 = new InlineKeyboardButton("❌ " + codes.get(i + 1)).callbackData("admin_rem_promo_" + codes.get(i + 1));
                    keyboard.addRow(btn1, btn2);
                } else {
                    keyboard.addRow(btn1);
                }
            }
        } else if (type.equals("akkaunt")) {
            for (int i = 0; i < akkaunts.size(); i += 2) {
                if (akkaunts.get(i).sold) continue;
                InlineKeyboardButton btn1 = new InlineKeyboardButton("❌ ID " + akkaunts.get(i).id + " - " + akkaunts.get(i).description).callbackData("admin_rem_akkaunt_" + akkaunts.get(i).id);
                if (i + 1 < akkaunts.size() && !akkaunts.get(i + 1).sold) {
                    InlineKeyboardButton btn2 = new InlineKeyboardButton("❌ ID " + akkaunts.get(i + 1).id + " - " + akkaunts.get(i + 1).description).callbackData("admin_rem_akkaunt_" + akkaunts.get(i + 1).id);
                    keyboard.addRow(btn1, btn2);
                } else {
                    keyboard.addRow(btn1);
                }
            }
        } else if (type.equals("card")) {
            for (int i = 0; i < paymentCards.size(); i += 2) {
                PaymentCard card1 = paymentCards.get(i);
                InlineKeyboardButton btn1 = new InlineKeyboardButton("❌ " + card1.name + " - " + card1.number).callbackData("admin_rem_card_" + i);
                if (i + 1 < paymentCards.size()) {
                    PaymentCard card2 = paymentCards.get(i + 1);
                    InlineKeyboardButton btn2 = new InlineKeyboardButton("❌ " + card2.name + " - " + card2.number).callbackData("admin_rem_card_" + (i + 1));
                    keyboard.addRow(btn1, btn2);
                } else {
                    keyboard.addRow(btn1);
                }
            }
        }
        keyboard.addRow(new InlineKeyboardButton(getText(lang, "back")).callbackData("admin_back_manage"));
        sendMessage(chatId, getText(lang, "select_to_remove"), keyboard, ParseMode.Markdown);
    }

    private void purchaseItem(long chatId, User user, String type, String description, int price) {
        if (user.eliksir < price) {
            sendMessage(chatId, getText(user.language, "not_enough_eliksir_detailed")
                    .replace("{needed}", String.valueOf(price))
                    .replace("{have}", String.valueOf(user.eliksir)), ParseMode.Markdown);
            return;
        }
        user.eliksir -= price;
        user.history.add(type + getText(user.language, "bought") + description + " (-" + price + " eliksir) 🧪");

        InventoryItem item = new InventoryItem();
        item.id = "item_" + inventoryIdGenerator.getAndIncrement();
        item.type = type;
        item.description = description;
        item.purchaseDate = LocalDateTime.now();
        item.details = getText(user.language, "user") + user.username + "\n" +
                getText(user.language, "service") + description + "\n" +
                getText(user.language, "price") + price + " eliksir 🧪\n" +
                getText(user.language, "date") + item.purchaseDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        item.userChatId = chatId;
        user.inventory.add(item);

        sendMessage(chatId, getText(user.language, "purchase_success") + "\n\n" + description + "\n\n" +
                getText(user.language, "current_balance") + "*" + user.eliksir + "* 🧪\n\n" +
                getText(user.language, "added_to_inventory"), ParseMode.Markdown);

        long adminId = getAdminChatId();
        if (adminId != 0) {
            sendMessage(adminId, getText("uz", "new_order") + user.username + "\nXizmat: " + type + " - " + description, ParseMode.Markdown);
        }
    }

    private void sendInventory(long chatId, User user) {
        if (user.inventory.isEmpty()) {
            sendMessage(chatId, getText(user.language, "inventory_empty"), ParseMode.Markdown);
            return;
        }
        StringBuilder sb = new StringBuilder(getText(user.language, "inventory") + "\n\n")
                .append("💡 *Buyurtmangiz qabul qilinganidan so'ng 24 soat ichida admin sizga yozadi va xizmatni bajaradi.* ⏳\n\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm");
        for (InventoryItem item : user.inventory) {
            sb.append("🔹 *").append(item.description).append("*\n");
            sb.append(" 📅 ").append(item.purchaseDate.format(formatter)).append("\n");
            sb.append(" 📌 ").append(item.completed ? getText(user.language, "completed") : getText(user.language, "pending")).append("\n");
            if (item.details != null && !item.details.isEmpty()) {
                sb.append(" 📝 ").append(item.details).append("\n");
            }
            sb.append("\n");
        }
        sendMessage(chatId, sb.toString(), ParseMode.Markdown);
    }

    private void completeTask(String itemId, long adminChatId, String lang) {
        for (User u : users.values()) {
            InventoryItem item = u.inventory.stream().filter(it -> it.id.equals(itemId)).findFirst().orElse(null);
            if (item != null) {
                item.completed = true;
                sendMessage(item.userChatId, getText(u.language, "order_completed") + "\n\n" + item.description, ParseMode.Markdown);
                sendMessage(adminChatId, getText(lang, "order_completed_success") + item.description, ParseMode.Markdown);
                if (!completedTasksChannel.isEmpty()) {
                    String channelMessage = "🎉✅ *Xizmat bajarildi!* ✅🎉\n\n" +
                            "👤 *Foydalanuvchi:* " + u.username + "\n" +
                            "🏆 *Xizmat:* " + item.description + "\n" +
                            "📅 *Sana:* " + item.purchaseDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n" +
                            "📝 *Tafsilotlar:* " + item.details + "\n\n" +
                            "🔥 Brawl Stars xizmatlarimizdan bahramand bo'ling! 🚀";
                    bot.execute(new SendMessage(completedTasksChannel, channelMessage).parseMode(ParseMode.Markdown));
                }
                return;
            }
        }
        sendMessage(adminChatId, getText(lang, "order_not_found"), ParseMode.Markdown);
    }

    private User getOrCreateUser(long chatId, String username) {
        return users.computeIfAbsent(chatId, k -> {
            User u = new User();
            u.username = username;
            u.isAdmin = username.equals(ADMIN_USERNAME);
            u.history.add("Botga qo'shildi: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            return u;
        });
    }

    private boolean checkSubscriptions(long chatId) {
        for (String channel : mandatoryChannels) {
            GetChatMemberResponse response = bot.execute(new GetChatMember(channel, chatId));
            if (!response.isOk()) return false;
            ChatMember.Status status = response.chatMember().status();
            if (!(status == ChatMember.Status.member || status == ChatMember.Status.administrator || status == ChatMember.Status.creator)) {
                return false;
            }
        }
        return true;
    }

    private void sendSubscriptionRequest(long chatId, String lang) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        for (String channel : mandatoryChannels) {
            String username = channel.substring(1);
            keyboard.addRow(new InlineKeyboardButton(getText(lang, "subscribe_to") + channel).url("https://t.me/" + username));
        }
        keyboard.addRow(new InlineKeyboardButton(getText(lang, "check")).callbackData("check_sub"));
        sendMessage(chatId, getText(lang, "mandatory_subscription"), keyboard, ParseMode.Markdown);
    }

    private void sendMainMenu(long chatId, String text, String lang) {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(
                new KeyboardButton("\uD83D\uDD79️ Serverlar")
        ).addRow(
                new KeyboardButton("\uD83D\uDC64 Profil"),
                new KeyboardButton("\uD83D\uDCB0 Hisob to'ldirish")
        ).addRow(
                new KeyboardButton("📦 Inventar")
        ).addRow(
                new KeyboardButton("\uD83C\uDF81 Promokod"),
                new KeyboardButton("☎️ Qo'llab-quvvatlash")
        ).resizeKeyboard(true);

        User user = users.get(chatId);
        if (user != null && user.isAdmin) {
            keyboard.addRow(new KeyboardButton("⚙️ Admin Panel"));
        }
        sendMessage(chatId, text, keyboard, ParseMode.Markdown);
    }

    private long getAdminChatId() {
        return users.entrySet().stream()
                .filter(e -> e.getValue().username.equals(ADMIN_USERNAME))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(0L);
    }

    private void processAdminInput(long chatId, String text, User user) {
        try {
            switch (user.awaitingInput) {
                case "add_channel" -> {
                    String channel = text.trim().toLowerCase();
                    if (!channel.startsWith("@")) channel = "@" + channel;
                    if (!mandatoryChannels.contains(channel)) {
                        mandatoryChannels.add(channel);
                        sendMessage(chatId, getText(user.language, "channel_added") + channel + " 📢", ParseMode.Markdown);
                    } else {
                        sendMessage(chatId, getText(user.language, "channel_exists"), ParseMode.Markdown);
                    }
                    showCurrentChannels(chatId, user.language);
                }
                case "add_promo" -> {
                    String[] p = text.trim().split(" ");
                    if (p.length < 2) {
                        sendMessage(chatId, getText(user.language, "promo_format_error"), ParseMode.Markdown);
                        return;
                    }
                    String code = p[0].toUpperCase();
                    int amount = Integer.parseInt(p[1]);
                    int limit = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                    PromoCode pc = new PromoCode();
                    pc.amount = amount;
                    pc.usageLimit = limit;
                    promoCodes.put(code, pc);
                    sendMessage(chatId, getText(user.language, "promo_added") + code + " 🔑", ParseMode.Markdown);
                    showCurrentPromocodes(chatId, user.language);
                }
                case "bonus" -> {
                    dailyBonus = Integer.parseInt(text.trim());
                    sendMessage(chatId, getText(user.language, "bonus_updated") + dailyBonus + " 🎁", ParseMode.Markdown);
                    sendAdminPanel(chatId, user.language);
                }
                case "akkaunt" -> {
                    String[] parts = text.split("\\|");
                    if (parts.length < 4) {
                        sendMessage(chatId, getText(user.language, "akkaunt_data_error"), ParseMode.Markdown);
                        return;
                    }
                    user.tempData = text;
                    user.awaitingInput = "akkaunt_file";
                    sendMessage(chatId, getText(user.language, "send_akkaunt_photo"), ParseMode.Markdown);
                    return;
                }
                case "add_card_name" -> {
                    user.tempData = text.trim();
                    user.awaitingInput = "add_card_number";
                    sendMessage(chatId, getText(user.language, "enter_new_card_number"), ParseMode.Markdown);
                    return;
                }
                case "add_card_number" -> {
                    PaymentCard card = new PaymentCard();
                    card.name = user.tempData;
                    card.number = text.trim();
                    paymentCards.add(card);
                    sendMessage(chatId, getText(user.language, "card_added") + card.name + " 💳", ParseMode.Markdown);
                    showCurrentCards(chatId, user.language);
                }
                case "broadcast_all" -> {
                    for (long id : users.keySet()) {
                        sendMessage(id, text, ParseMode.Markdown);
                    }
                    sendMessage(chatId, getText(user.language, "broadcast_sent_all"), ParseMode.Markdown);
                }
                case "broadcast_username" -> {
                    user.tempData = text.trim();
                    user.awaitingInput = "broadcast_single";
                    sendMessage(chatId, getText(user.language, "enter_broadcast_message"), ParseMode.Markdown);
                    return;
                }
                case "broadcast_single" -> {
                    long targetId = users.entrySet().stream()
                            .filter(e -> e.getValue().username.equals(user.tempData))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(0L);
                    if (targetId != 0) {
                        sendMessage(targetId, text, ParseMode.Markdown);
                        sendMessage(chatId, getText(user.language, "broadcast_sent_single") + user.tempData, ParseMode.Markdown);
                    } else {
                        sendMessage(chatId, getText(user.language, "user_not_found"), ParseMode.Markdown);
                    }
                }
                case "elixir_username" -> {
                    user.tempData = text.trim();
                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup()
                            .addRow(new InlineKeyboardButton("+ Qo'shish").callbackData("admin_elixir_add"),
                                    new InlineKeyboardButton("- Ayirish").callbackData("admin_elixir_subtract"))
                            .addRow(new InlineKeyboardButton("Ko'rish").callbackData("admin_elixir_view"));
                    sendMessage(chatId, getText(user.language, "select_elixir_action"), keyboard);
                    return;
                }
                case "elixir_amount_add" -> {
                    User target = findUserByUsername(user.tempData);
                    if (target != null) {
                        int amount = Integer.parseInt(text.trim());
                        target.eliksir += amount;
                        sendMessage(chatId, getText(user.language, "elixir_added") + "+" + amount + " 🧪", ParseMode.Markdown);
                    } else {
                        sendMessage(chatId, getText(user.language, "user_not_found"), ParseMode.Markdown);
                    }
                }
                case "elixir_amount_subtract" -> {
                    User target = findUserByUsername(user.tempData);
                    if (target != null) {
                        int amount = Integer.parseInt(text.trim());
                        target.eliksir -= amount;
                        sendMessage(chatId, getText(user.language, "elixir_subtracted") + "-" + amount + " 🧪", ParseMode.Markdown);
                    } else {
                        sendMessage(chatId, getText(user.language, "user_not_found"), ParseMode.Markdown);
                    }
                }
                case "referral_bonus" -> {
                    referralBonus = Integer.parseInt(text.trim());
                    sendMessage(chatId, getText(user.language, "referral_bonus_updated") + referralBonus + " 👥", ParseMode.Markdown);
                }
                case "completed_channel" -> {
                    completedTasksChannel = text.trim().toLowerCase();
                    if (!completedTasksChannel.startsWith("@")) completedTasksChannel = "@" + completedTasksChannel;
                    sendMessage(chatId, getText(user.language, "completed_channel_updated") + completedTasksChannel + " 🔔", ParseMode.Markdown);
                }
            }
        } catch (Exception e) {
            sendMessage(chatId, getText(user.language, "error") + e.getMessage() + "\nQayta urinib ko'ring. ❌", ParseMode.Markdown);
        }
        user.awaitingInput = null;
        user.tempData = null;
    }

    private User findUserByUsername(String username) {
        return users.values().stream().filter(u -> u.username.equals(username)).findFirst().orElse(null);
    }

    private void processAdminAkkauntWithPhoto(long chatId, User user, String fileId) {
        String[] parts = user.tempData.split("\\|");
        if (parts.length < 4) {
            sendMessage(chatId, getText(user.language, "akkaunt_data_error"), ParseMode.Markdown);
            user.awaitingInput = "akkaunt";
            return;
        }
        Akkaunt a = new Akkaunt();
        a.id = akkauntIdGenerator.getAndIncrement();
        a.description = parts[0].trim();
        a.price = Integer.parseInt(parts[1].trim());
        a.gmail = parts[2].trim();
        a.password = parts[3].trim();
        a.imageFileId = fileId;
        akkaunts.add(a);
        sendMessage(chatId, getText(user.language, "akkaunt_added_success") + a.id + " 🌟", ParseMode.Markdown);
        user.awaitingInput = null;
        user.tempData = null;
        showCurrentAkkaunts(chatId, user.language);
    }

    private void sendMessage(long chatId, String text) {
        bot.execute(new SendMessage(chatId, text));
    }

    private void sendMessage(long chatId, String text, Keyboard keyboard) {
        bot.execute(new SendMessage(chatId, text).replyMarkup(keyboard));
    }

    private void sendMessage(long chatId, String text, Keyboard keyboard, ParseMode mode) {
        bot.execute(new SendMessage(chatId, text).replyMarkup(keyboard).parseMode(mode));
    }

    private void sendMessage(long chatId, String text, ParseMode mode) {
        bot.execute(new SendMessage(chatId, text).parseMode(mode));
    }

    private String getText(String lang, String key) {
        Map<String, Map<String, String>> texts = Map.of(
                "uz", Map.ofEntries(
                        Map.entry("send_photo_check", "🛑 Iltimos, chek rasmini yuboring (foto sifatida)! 📸"),
                        Map.entry("send_photo", "\uD83D\uDEAB Iltimos, rasm yuboring (foto sifatida)!"),
                        Map.entry("unknown_command", "🛑 Noma'lum buyruq! 🚫"),
                        Map.entry("main_menu", "🌟 Asosiy menyu:"),
                        Map.entry("subscribe_first", "❌ Avval majburiy kanallarga obuna bo'ling!"),
                        Map.entry("subscribe_all", "🛑 Barcha kanallarga obuna bo'ling! 📢"),
                        Map.entry("welcome_back", "🔥 Xush kelibsiz qayta! 🌟"),
                        Map.entry("canceled", "🛑 Bekor qilindi! ❌"),
                        Map.entry("admin_exit_message", "✅ Admin paneldan chiqdingiz!\nAsosiy menyuga qaytdingiz 🔥"),
                        Map.entry("support_menu", "☎️ *Qo'llab-quvvatlash* 🌟"),
                        Map.entry("faq_button", "Qo'llanma"),
                        Map.entry("admin_button", "Admin"),
                        Map.entry("contact_admin", "Admin bilan bog'laning: "),
                        Map.entry("server_services", "\uD83D\uDD79️ *Server xizmatlari* 🌟\nTanlang:"),
                        Map.entry("bust_service", "🏆 *Bust xizmati* 🌟"),
                        Map.entry("supercell_id", "🔐 Supercell ID orqali ishlaymiz! 🔒"),
                        Map.entry("buy", "🛒 Sotib olish"),
                        Map.entry("select_bust", "🏆 Bust variantini tanlang: 🌟"),
                        Map.entry("confirm_buy", "Haqiqatan sotib olmoqchimisiz?\n\n"),
                        Map.entry("price", "Narxi: "),
                        Map.entry("yes", "Ha ✅"),
                        Map.entry("no", "Yo'q ❌"),
                        Map.entry("select_donat", "💎 *Donat xizmatini tanlang* 🌟"),
                        Map.entry("no_akkaunts", "🛑 Hozircha akkaunt yo'q 😅"),
                        Map.entry("akkaunt_sold_or_not_found", "🛑 Akkaunt sotilgan yoki mavjud emas! ❌"),
                        Map.entry("not_enough_eliksir", "🛑 Eliksir yetarli emas! ❌"),
                        Map.entry("akkaunt_bought", "Akkaunt sotib olindi: "),
                        Map.entry("bought_success", "\uD83C\uDF89 *Sotib olindi!* 🎉"),
                        Map.entry("added_to_inventory", "Inventarga qo'shildi!"),
                        Map.entry("profile", "\uD83D\uDC64 *Profil* 🌟"),
                        Map.entry("user", "👤 Foydalanuvchi: "),
                        Map.entry("balance", "💰 Balans: "),
                        Map.entry("referrals", "👥 Referallar: "),
                        Map.entry("total_donat", "💸 Jami donat: "),
                        Map.entry("days_in_bot", "📅 Botda: "),
                        Map.entry("top_up", "\uD83D\uDCB0 *Hisob to'ldirish* 🌟"),
                        Map.entry("daily_bonus_claimed", "Kunlik bonus: "),
                        Map.entry("bonus_claimed", "\uD83C\uDF89 *Bonus olindi!* 🎉 "),
                        Map.entry("already_claimed_today", "⏳ Bugun allaqachon oldingiz! Ertaga keling. 📅"),
                        Map.entry("enter_promo", "\uD83C\uDF81 Promokodni kiriting: 🔑"),
                        Map.entry("promo_already_used", "🛑 Siz bu promokodni allaqachon ishlatgansiz! ❌"),
                        Map.entry("promo_success", "\uD83C\uDF89 *Muvaffaqiyatli!* 🎉 "),
                        Map.entry("promo_expired", "🛑 Promokod tugagan! ❌"),
                        Map.entry("invalid_promo", "🛑 Noto'g'ri promokod! ❌"),
                        Map.entry("promo_applied", "Promokod: "),
                        Map.entry("payment_options", "💳 *To'lov variantlari* 🌟"),
                        Map.entry("no_payment_method", "🛑 Hozircha to'lov usuli mavjud emas! Admin bilan bog'laning. 📞"),
                        Map.entry("back", "🔙 Orqaga"),
                        Map.entry("payment_details", "💳 *To'lov ma'lumotlari* 🌟"),
                        Map.entry("selected", "Tanlangan: "),
                        Map.entry("amount", "Summa: "),
                        Map.entry("cards", "Kartalar:"),
                        Map.entry("send_check_photo", "To'lovni amalga oshirib, chek rasmini yuboring! 📸"),
                        Map.entry("confirm_and_pay", "✅ Tasdiqlash va to'lov qilish"),
                        Map.entry("send_check_photo_request", "📸 To'lov chek rasmini yuboring! (Foto sifatida) 🌟"),
                        Map.entry("error_option_not_found", "🛑 Xato: Variant topilmadi!"),
                        Map.entry("check_accepted", "✅ Chek qabul qilindi! Admin tasdiqlashini kuting. ⏳🌟"),
                        Map.entry("payment_approved", "🎉 *To'lov tasdiqlandi!* ✅\n"),
                        Map.entry("donat_added", "Donat: "),
                        Map.entry("admin_panel", "\uD83D\uDD25 *ADMIN PANEL* ⚙️\n\nKerakli bo'limni tanlang: 🌟"),
                        Map.entry("admin_use_buttons", "Admin panel tugmalar orqali ishlaydi. Matnli buyruqlar qo'llab-quvvatlanmaydi. ⚙️"),
                        Map.entry("mandatory_channels", "🔗 *Majburiy kanallar:* 📢"),
                        Map.entry("none", "Hozircha yo'q 😅"),
                        Map.entry("add", "➕ Qo'shish"),
                        Map.entry("remove", "❌ O'chirish"),
                        Map.entry("promocodes", "🔑 *Promokodlar:* 🌟"),
                        Map.entry("unlimited", "cheksiz"),
                        Map.entry("remaining", "qolgan"),
                        Map.entry("akkaunts", "🔓 *Akkauntlar:* 🌟"),
                        Map.entry("current_cards", "💳 *Joriy kartalar* 🌟"),
                        Map.entry("no_pending_tasks", "📋 Hozircha yangi vazifalar yo'q! ⏳"),
                        Map.entry("pending_tasks", "📋 *Yangi vazifalar* 🌟"),
                        Map.entry("completed", "✅ Bajarildi"),
                        Map.entry("pending", "⏳ Jarayonda"),
                        Map.entry("statistics", "📊 *Bot statistikasi* 🌟"),
                        Map.entry("total_users", "👥 Foydalanuvchilar: "),
                        Map.entry("total_channels", "🔗 Majburiy kanallar: "),
                        Map.entry("total_promocodes", "🔑 Promokodlar: "),
                        Map.entry("total_orders", "📦 Jami buyurtmalar: "),
                        Map.entry("completed_orders", "✅ Bajarilgan: "),
                        Map.entry("pending_orders", "⏳ Jarayonda: "),
                        Map.entry("top_5", "🏆 Top 5 (eliksir bo'yicha):"),
                        Map.entry("select_to_remove", "O'chirish uchun tanlang: ❌"),
                        Map.entry("removed", "❌ O'chirildi: "),
                        Map.entry("promo_removed", "❌ Promokod o'chirildi: "),
                        Map.entry("akkaunt_removed", "❌ Akkaunt o'chirildi: ID "),
                        Map.entry("card_removed", "❌ Karta o'chirildi!"),
                        Map.entry("not_enough_eliksir_detailed", "🛑 Eliksir yetarli emas! (kerak: {needed}, bor: {have}) ❌"),
                        Map.entry("bought", " sotib olindi: "),
                        Map.entry("purchase_success", "\uD83C\uDF89 *Muvaffaqiyatli sotib olindi!* 🎉"),
                        Map.entry("current_balance", "Joriy balans: "),
                        Map.entry("inventory_empty", "📦 Inventaringiz bo'sh! 😅"),
                        Map.entry("inventory", "📦 *Inventar* 🌟"),
                        Map.entry("order_completed", "\uD83C\uDF89 Sizning buyurtmangiz bajarildi! ✅"),
                        Map.entry("order_completed_success", "✅ Buyurtma bajarildi: "),
                        Map.entry("order_not_found", "🛑 Buyurtma topilmadi! 🚫"),
                        Map.entry("mandatory_subscription", "\uD83D\uDD14 *Majburiy obuna!* 📢\nQuyidagi kanallarga obuna bo'ling:"),
                        Map.entry("subscribe_to", "🔔 Obuna bo'lish "),
                        Map.entry("check", "✅ Tekshirish"),
                        Map.entry("channel_added", "✅ Kanal qo'shildi: "),
                        Map.entry("channel_exists", "\uD83D\uDEAB Bu kanal allaqachon mavjud! ❌"),
                        Map.entry("promo_format_error", "🛑 Format: KOD miqdor [limit] ❌"),
                        Map.entry("promo_added", "✅ Promokod qo'shildi: "),
                        Map.entry("bonus_updated", "✅ Kunlik bonus o'zgartirildi: "),
                        Map.entry("akkaunt_data_error", "🛑 Ma'lumotlar noto'g'ri!\nFormat: Tavsif|Narx|Gmail|Parol ❌"),
                        Map.entry("akkaunt_added_success", "✅ Akkaunt muvaffaqiyatli qo'shildi! ID: "),
                        Map.entry("card_added", "✅ Karta qo'shildi: "),
                        Map.entry("error", "🛑 Xato: "),
                        Map.entry("add_channel_prompt", "➕ Yangi kanal username'ini kiriting (masalan: @meningkanal): 📢"),
                        Map.entry("add_promo_prompt", "➕ Promokod formatida kiriting:\nKOD miqdor limit (masalan: TEST123 500 10)\n0 = cheksiz 🔑"),
                        Map.entry("add_akkaunt_prompt", "➕ Yangi akkaunt ma'lumotlarini kiriting:\nTavsif|Narx|Gmail|Parol\nKeyin rasm yuboring. 📸"),
                        Map.entry("enter_new_bonus", "Yangi kunlik bonus miqdorini kiriting: 🔢"),
                        Map.entry("send_akkaunt_photo", "📸 Endi akkaunt rasmini yuboring:"),
                        Map.entry("welcome_message", "🔥 **Salom, {username}!** 🔥\n\n🌟 Brawl Stars xizmatlari botiga xush kelibsiz! ⚔️\n\n🏆 Bust | 💎 Donat | 🔑 Akkauntlar\n\n💰 Joriy balansingiz: *{eliksir}* eliksir 🧪\n\nPastdagi tugmalar orqali xizmatni tanlang va zavqlaning! 🚀"),
                        Map.entry("new_referral", "Yangi referal: +"),
                        Map.entry("new_referral_notify", "\uD83C\uDF89 Yangi referal keldi! +"),
                        Map.entry("referal_link", "\uD83D\uDC65 *Referal havola* 🔗\n"),
                        Map.entry("new_payment_check", "🔔 *Yangi to'lov cheki!* 💰\nFoydalanuvchi: "),
                        Map.entry("payment_approved_success", "✅ To'lov tasdiqlandi va eliksir qo'shildi! 🌟"),
                        Map.entry("akkaunt_sold_notify", "\uD83D\uDD14 *Akkaunt sotildi!* 🔔\nFoydalanuvchi: "),
                        Map.entry("new_order", "\uD83D\uDD14 *Yangi buyurtma!* 🔔\n\nFoydalanuvchi: "),
                        Map.entry("service", "Xizmat: "),
                        Map.entry("date", "Sana: "),
                        Map.entry("referal", "👥 Referal"),
                        Map.entry("daily_bonus", "🎁 Kunlik bonus"),
                        Map.entry("payment_card", "💳 Karta orqali"),
                        Map.entry("broadcast_type_prompt", "📢 Xabar turini tanlang:"),
                        Map.entry("enter_elixir_username", "🧪 Foydalanuvchi username'ini kiriting (@ bilan):"),
                        Map.entry("select_elixir_action", "🧪 Amalni tanlang:"),
                        Map.entry("enter_add_amount", "➕ Qo'shiladigan miqdorni kiriting:"),
                        Map.entry("enter_subtract_amount", "➖ Ayiriladigan miqdorni kiriting:"),
                        Map.entry("elixir_view", "🧪 Eliksir: "),
                        Map.entry("elixir_added", "✅ Eliksir qo'shildi: "),
                        Map.entry("elixir_subtracted", "✅ Eliksir ayirildi: "),
                        Map.entry("user_not_found", "🛑 Foydalanuvchi topilmadi!"),
                        Map.entry("enter_new_referral_bonus", "👥 Yangi referal bonus miqdorini kiriting:"),
                        Map.entry("referral_bonus_updated", "✅ Referal bonus o'zgartirildi: "),
                        Map.entry("enter_completed_channel", "🔔 Bajarilgan vazifalar uchun kanal username'ini kiriting (@ bilan):"),
                        Map.entry("completed_channel_updated", "✅ Kanal o'zgartirildi: "),
                        Map.entry("enter_new_card_name", "💳 Yangi karta nomini kiriting:"),
                        Map.entry("enter_new_card_number", "💳 Karta raqamini kiriting:"),
                        Map.entry("broadcast_sent_all", "📢 Xabar hammaga yuborildi!"),
                        Map.entry("enter_broadcast_all", "📢 Hammaga yuboriladigan xabarni kiriting:"),
                        Map.entry("enter_broadcast_username", "👤 Foydalanuvchi username'ini kiriting (@ bilan):"),
                        Map.entry("enter_broadcast_message", "📝 Yuboriladigan xabarni kiriting:"),
                        Map.entry("broadcast_sent_single", "📢 Xabar yuborildi: ")
                ),
                "ru", Map.ofEntries(
                        Map.entry("send_photo_check", "🛑 Пожалуйста, отправьте фото чека! 📸"),
                        Map.entry("send_photo", "🛑 Пожалуйста, отправьте фото!"),
                        Map.entry("unknown_command", "🛑 Неизвестная команда! 🚫"),
                        Map.entry("main_menu", "🌟 Главное меню:"),
                        Map.entry("subscribe_first", "❌ Сначала подпишитесь на обязательные каналы!"),
                        Map.entry("subscribe_all", "🛑 Подпишитесь на все каналы! 📢"),
                        Map.entry("welcome_back", "🔥 Добро пожаловать обратно! 🌟"),
                        Map.entry("canceled", "🛑 Отменено! ❌"),
                        Map.entry("admin_exit_message", "✅ Вы вышли из админ-панели!\nВернулись в главное меню 🔥"),
                        Map.entry("support_menu", "☎️ *Поддержка* 🌟"),
                        Map.entry("faq_button", "Руководство"),
                        Map.entry("admin_button", "Админ"),
                        Map.entry("contact_admin", "Свяжитесь с админом: "),
                        Map.entry("server_services", "\uD83D\uDD79️ *Серверные услуги* 🌟\nВыберите:"),
                        Map.entry("bust_service", "🏆 *Буст услуга* 🌟"),
                        Map.entry("supercell_id", "🔐 Работаем через Supercell ID! 🔒"),
                        Map.entry("buy", "🛒 Купить"),
                        Map.entry("select_bust", "🏆 Выберите вариант буста: 🌟"),
                        Map.entry("confirm_buy", "Действительно хотите купить?\n\n"),
                        Map.entry("price", "Цена: "),
                        Map.entry("yes", "Да ✅"),
                        Map.entry("no", "Нет ❌"),
                        Map.entry("select_donat", "💎 *Выберите донат* 🌟"),
                        Map.entry("no_akkaunts", "🛑 Пока нет аккаунтов 😅"),
                        Map.entry("akkaunt_sold_or_not_found", "🛑 Аккаунт продан или не существует! ❌"),
                        Map.entry("not_enough_eliksir", "🛑 Недостаточно эликсира! ❌"),
                        Map.entry("akkaunt_bought", "Аккаунт куплен: "),
                        Map.entry("bought_success", "\uD83C\uDF89 *Куплено!* 🎉"),
                        Map.entry("added_to_inventory", "Добавлено в инвентарь!"),
                        Map.entry("profile", "\uD83D\uDC64 *Профиль* 🌟"),
                        Map.entry("user", "👤 Пользователь: "),
                        Map.entry("balance", "💰 Баланс: "),
                        Map.entry("referrals", "👥 Рефералы: "),
                        Map.entry("total_donat", "💸 Всего доната: "),
                        Map.entry("days_in_bot", "📅 В боте: "),
                        Map.entry("top_up", "\uD83D\uDCB0 *Пополнение* 🌟"),
                        Map.entry("daily_bonus_claimed", "Ежедневный бонус: "),
                        Map.entry("bonus_claimed", "\uD83C\uDF89 *Бонус получен!* 🎉 "),
                        Map.entry("already_claimed_today", "⏳ Сегодня уже получено! Приходите завтра. 📅"),
                        Map.entry("enter_promo", "\uD83C\uDF81 Введите промокод: 🔑"),
                        Map.entry("promo_already_used", "🛑 Вы уже использовали этот промокод! ❌"),
                        Map.entry("promo_success", "\uD83C\uDF89 *Успешно!* 🎉 "),
                        Map.entry("promo_expired", "🛑 Промокод истек! ❌"),
                        Map.entry("invalid_promo", "🛑 Неверный промокод! ❌"),
                        Map.entry("promo_applied", "Промокод: "),
                        Map.entry("payment_options", "💳 *Варианты оплаты* 🌟"),
                        Map.entry("no_payment_method", "🛑 Пока нет методов оплаты! Свяжитесь с админом. 📞"),
                        Map.entry("back", "🔙 Назад"),
                        Map.entry("payment_details", "💳 *Детали оплаты* 🌟"),
                        Map.entry("selected", "Выбрано: "),
                        Map.entry("amount", "Сумма: "),
                        Map.entry("cards", "Карты:"),
                        Map.entry("send_check_photo", "Оплатите и отправьте фото чека! 📸"),
                        Map.entry("confirm_and_pay", "✅ Подтвердить и оплатить"),
                        Map.entry("send_check_photo_request", "📸 Отправьте фото чека оплаты! 🌟"),
                        Map.entry("error_option_not_found", "🛑 Ошибка: Вариант не найден!"),
                        Map.entry("check_accepted", "✅ Чек принят! Ждите подтверждения админа. ⏳🌟"),
                        Map.entry("payment_approved", "🎉 *Оплата подтверждена!* ✅\n"),
                        Map.entry("donat_added", "Донат: "),
                        Map.entry("admin_panel", "\uD83D\uDD25 *АДМИН ПАНЕЛЬ* ⚙️\n\nВыберите раздел: 🌟"),
                        Map.entry("admin_use_buttons", "Админ-панель работает через кнопки. Текстовые команды не поддерживаются. ⚙️"),
                        Map.entry("mandatory_channels", "🔗 *Обязательные каналы:* 📢"),
                        Map.entry("none", "Пока нет 😅"),
                        Map.entry("add", "➕ Добавить"),
                        Map.entry("remove", "❌ Удалить"),
                        Map.entry("promocodes", "🔑 *Промокоды:* 🌟"),
                        Map.entry("unlimited", "бесконечно"),
                        Map.entry("remaining", "осталось"),
                        Map.entry("akkaunts", "🔓 *Аккаунты:* 🌟"),
                        Map.entry("current_cards", "💳 *Текущие карты* 🌟"),
                        Map.entry("no_pending_tasks", "📋 Пока нет новых задач! ⏳"),
                        Map.entry("pending_tasks", "📋 *Новые задачи* 🌟"),
                        Map.entry("completed", "✅ Выполнено"),
                        Map.entry("pending", "⏳ В ожидании"),
                        Map.entry("statistics", "📊 *Статистика бота* 🌟"),
                        Map.entry("total_users", "👥 Пользователи: "),
                        Map.entry("total_channels", "🔗 Обязательные каналы: "),
                        Map.entry("total_promocodes", "🔑 Промокоды: "),
                        Map.entry("total_orders", "📦 Всего заказов: "),
                        Map.entry("completed_orders", "✅ Выполнено: "),
                        Map.entry("pending_orders", "⏳ В процессе: "),
                        Map.entry("top_5", "🏆 Топ 5 (по эликсиру):"),
                        Map.entry("select_to_remove", "Выберите для удаления: ❌"),
                        Map.entry("removed", "❌ Удалено: "),
                        Map.entry("promo_removed", "❌ Промокод удален: "),
                        Map.entry("akkaunt_removed", "❌ Аккаунт удален: ID "),
                        Map.entry("card_removed", "❌ Карта удалена!"),
                        Map.entry("not_enough_eliksir_detailed", "🛑 Недостаточно эликсира! (нужно: {needed}, есть: {have}) ❌"),
                        Map.entry("bought", " куплено: "),
                        Map.entry("purchase_success", "\uD83C\uDF89 *Успешно куплено!* 🎉"),
                        Map.entry("current_balance", "Текущий баланс: "),
                        Map.entry("inventory_empty", "📦 Ваш инвентарь пуст! 😅"),
                        Map.entry("inventory", "📦 *Инвентарь* 🌟"),
                        Map.entry("order_completed", "\uD83C\uDF89 Ваш заказ выполнен! ✅"),
                        Map.entry("order_completed_success", "✅ Заказ выполнен: "),
                        Map.entry("order_not_found", "🛑 Заказ не найден! 🚫"),
                        Map.entry("mandatory_subscription", "\uD83D\uDD14 *Обязательная подписка!* 📢\nПодпишитесь на следующие каналы:"),
                        Map.entry("subscribe_to", "🔔 Подписаться "),
                        Map.entry("check", "✅ Проверить"),
                        Map.entry("channel_added", "✅ Канал добавлен: "),
                        Map.entry("channel_exists", "\uD83D\uDEAB Этот канал уже существует! ❌"),
                        Map.entry("promo_format_error", "🛑 Формат: КОД сумма [лимит] ❌"),
                        Map.entry("promo_added", "✅ Промокод добавлен: "),
                        Map.entry("bonus_updated", "✅ Ежедневный бонус изменен: "),
                        Map.entry("akkaunt_data_error", "🛑 Данные неверны!\nФормат: Описание|Цена|Gmail|Пароль ❌"),
                        Map.entry("akkaunt_added_success", "✅ Аккаунт успешно добавлен! ID: "),
                        Map.entry("card_added", "✅ Карта добавлена: "),
                        Map.entry("error", "🛑 Ошибка: "),
                        Map.entry("add_channel_prompt", "➕ Введите username нового канала (например: @mychannel): 📢"),
                        Map.entry("add_promo_prompt", "➕ Введите промокод в формате:\nКОД сумма лимит (например: TEST123 500 10)\n0 = бесконечно 🔑"),
                        Map.entry("add_akkaunt_prompt", "➕ Введите данные нового аккаунта:\nОписание|Цена|Gmail|Пароль\nЗатем отправьте фото. 📸"),
                        Map.entry("enter_new_bonus", "Введите новую сумму ежедневного бонуса: 🔢"),
                        Map.entry("send_akkaunt_photo", "📸 Теперь отправьте фото аккаунта:"),
                        Map.entry("welcome_message", "🔥 **Привет, {username}!** 🔥\n\n🌟 Добро пожаловать в бот услуг Brawl Stars! ⚔️\n\n🏆 Буст | 💎 Донат | 🔑 Аккаунты\n\n💰 Текущий баланс: *{eliksir}* эликсир 🧪\n\nВыберите услугу через кнопки ниже и наслаждайтесь! 🚀"),
                        Map.entry("new_referral", "Новый реферал: +"),
                        Map.entry("new_referral_notify", "\uD83C\uDF89 Новый реферал пришел! +"),
                        Map.entry("referal_link", "\uD83D\uDC65 *Реферальная ссылка* 🔗\n"),
                        Map.entry("new_payment_check", "🔔 *Новый чек оплаты!* 💰\nПользователь: "),
                        Map.entry("payment_approved_success", "✅ Оплата подтверждена и эликсир добавлен! 🌟"),
                        Map.entry("akkaunt_sold_notify", "\uD83D\uDD14 *Аккаунт продан!* 🔔\nПользователь: "),
                        Map.entry("new_order", "\uD83D\uDD14 *Новый заказ!* 🔔\n\nПользователь: "),
                        Map.entry("service", "Услуга: "),
                        Map.entry("date", "Дата: "),
                        Map.entry("referal", "👥 Реферал"),
                        Map.entry("daily_bonus", "🎁 Ежедневный бонус"),
                        Map.entry("payment_card", "💳 Через карту"),
                        Map.entry("broadcast_type_prompt", "📢 Выберите тип сообщения:"),
                        Map.entry("enter_elixir_username", "🧪 Введите username пользователя (@ с):"),
                        Map.entry("select_elixir_action", "🧪 Выберите действие:"),
                        Map.entry("enter_add_amount", "➕ Введите сумму для добавления:"),
                        Map.entry("enter_subtract_amount", "➖ Введите сумму для вычитания:"),
                        Map.entry("elixir_view", "🧪 Эликсир: "),
                        Map.entry("elixir_added", "✅ Эликсир добавлен: "),
                        Map.entry("elixir_subtracted", "✅ Эликсир вычтен: "),
                        Map.entry("user_not_found", "🛑 Пользователь не найден!"),
                        Map.entry("enter_new_referral_bonus", "👥 Введите новую сумму реферального бонуса:"),
                        Map.entry("referral_bonus_updated", "✅ Реферальный бонус изменен: "),
                        Map.entry("enter_completed_channel", "🔔 Введите username канала для выполненных задач (@ с):"),
                        Map.entry("completed_channel_updated", "✅ Канал изменен: "),
                        Map.entry("enter_new_card_name", "💳 Введите имя новой карты:"),
                        Map.entry("enter_new_card_number", "💳 Введите номер карты:"),
                        Map.entry("broadcast_sent_all", "📢 Сообщение отправлено всем!"),
                        Map.entry("enter_broadcast_all", "📢 Введите сообщение для всех:"),
                        Map.entry("enter_broadcast_username", "👤 Введите username пользователя (@ с):"),
                        Map.entry("enter_broadcast_message", "📝 Введите отправляемое сообщение:"),
                        Map.entry("broadcast_sent_single", "📢 Сообщение отправлено: ")
                )
        );
        return texts.getOrDefault(lang, texts.get("uz")).getOrDefault(key, "Missing text: " + key);
    }
}