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
        return "/1227 - open the 1227 menu";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        InputManager.INSTANCE.requestOpen();
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
