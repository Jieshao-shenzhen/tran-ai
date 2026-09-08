@echo off
rem ============================================
rem  实训室管理系统 - 一键停止脚本
rem  按窗口标题结束各服务及其子 JVM
rem ============================================

taskkill /T /F /FI "WINDOWTITLE eq user*"      2>nul
taskkill /T /F /FI "WINDOWTITLE eq resource*"  2>nul
taskkill /T /F /FI "WINDOWTITLE eq business*"  2>nul
taskkill /T /F /FI "WINDOWTITLE eq report*"    2>nul
taskkill /T /F /FI "WINDOWTITLE eq gateway*"   2>nul
taskkill /T /F /FI "WINDOWTITLE eq frontend*"  2>nul
taskkill /T /F /FI "WINDOWTITLE eq nacos*"     2>nul

echo Stopped. 前后端服务已全部结束（Nacos 窗口若仍在可手动关闭）。