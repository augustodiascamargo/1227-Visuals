package net.m1227.core;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

/** Client-side "/1227": always opens the menu, even with no key bound. */
public final class Command1227 extends CommandBase {
    @Override
    public String getCommandName() {
        return "1227";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/1227 [hud|export|import] - menu, HUD editor, clipboard config";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String a = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        if (a.equals("hud")) {
            InputManager.INSTANCE.requestScreen(new net.m1227.hud.HudEditorScreen());
        } else if (a.equals("export")) {
            ConfigManager.exportToClipboard();
        } else if (a.equals("import")) {
            ConfigManager.importFromClipboard();
        } else {
            InputManager.INSTANCE.requestOpen();
        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
