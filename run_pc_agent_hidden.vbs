Set WshShell = CreateObject("WScript.Shell")
WshShell.Run "cmd /c python pc_agent.py", 0, False
