package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.*;
import net.earthcomputer.clientcommands.command.FindBlockCommand;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;

public class ClientCommands implements ClientModInitializer {
    @Override
    public void onInitializeClient() {

    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        ClientCommandManager.clearClientSideCommands();
        BookCommand.register(dispatcher);
        LookCommand.register(dispatcher);
        NoteCommand.register(dispatcher);
        ShrugCommand.register(dispatcher);
        FindCommand.register(dispatcher);
        FindBlockCommand.register(dispatcher);
        FindItemCommand.register(dispatcher);
        TaskCommand.register(dispatcher);
        CalcCommand.register(dispatcher);
        TempRuleCommand.register(dispatcher);
        RenderCommand.register(dispatcher);
        CHelpCommand.register(dispatcher);
        WikiCommand.register(dispatcher);
        CEnchantCommand.register(dispatcher);
        GlowCommand.register(dispatcher);
        GetDataCommand.register(dispatcher);

        CrackRNGCommand.register(dispatcher);
        
        if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
            CheatCrackRNGCommand.register(dispatcher);
        }
    }
}
