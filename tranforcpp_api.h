#ifndef TRANFORCPP_API_H
#define TRANFORCPP_API_H

#include <string>
#include <iostream>
#include <sstream>
#include <thread>
#include <atomic>

extern "C" {

void onPlayerJoin(const char* playerName);
void onPlayerQuit(const char* playerName);
void onPlayerChat(const char* playerName, const char* message);
void onShutdown();
void onBlockBreak(const char* playerName, const char* blockType);
void onBlockPlace(const char* playerName, const char* blockType);
void onBlockIgnite(const char* playerName, const char* blockType);
void onEntityDamage(const char* entityName, const char* damage);
void onEntityDeath(const char* entityName);
void onEntitySpawn(const char* entityType);
void onPlayerDeath(const char* playerName, const char* deathMessage);
void onPlayerMove(const char* playerName);
void onPlayerRespawn(const char* playerName);
void onPlayerInteract(const char* playerName, const char* action, const char* itemType);
void onPlayerDropItem(const char* playerName, const char* itemType);
void onPlayerPickupItem(const char* playerName, const char* itemType);
void onInventoryClick(const char* playerName, const char* slot, const char* itemType);
void onInventoryOpen(const char* playerName);
void onInventoryClose(const char* playerName);
void onServerCommand(const char* sender, const char* command);
void onWorldLoad(const char* worldName);
void onWeatherChange(const char* worldName, const char* weatherState);
void onHangingBreak(const char* entityType, const char* cause);

}

void broadcast(const char* message);
void sendMsg(const char* player, const char* message);
void console(const char* message);
void dispatchCommand(const char* command, bool sync = false);

namespace tranforcpp {
    std::atomic<bool> running(true);

    inline void sendMessage(const std::string& action, const std::string& msg) {
        std::cout << msg << std::endl;
    }
}

void broadcast(const char* message) {
    std::ostringstream oss;
    oss << R"({"action":"broadcast","message":")" << message << R"("})";
    tranforcpp::sendMessage("broadcast", oss.str());
}

void sendMessage(const char* player, const char* message) {
    std::ostringstream oss;
    oss << R"({"action":"sendMessage","player":")" << player
        << R"(","message":")" << message << R"("})";
    tranforcpp::sendMessage("sendMessage", oss.str());
}

void console(const char* message) {
    std::ostringstream oss;
    oss << R"({"action":"console","message":")" << message << R"("})";
    tranforcpp::sendMessage("console", oss.str());
}

void dispatchCommand(const char* command, bool sync) {
    std::ostringstream oss;
    oss << R"({"action":"dispatchCommand","command":")" << command << R"(","sync":)" << (sync ? "true" : "false") << R"(})";
    tranforcpp::sendMessage("dispatchCommand", oss.str());
}

#endif
