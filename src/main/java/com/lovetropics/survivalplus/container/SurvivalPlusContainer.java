package com.lovetropics.survivalplus.container;

import java.util.List;

import com.lovetropics.survivalplus.SPConfigs;
import com.lovetropics.survivalplus.SurvivalPlus;
import com.lovetropics.survivalplus.message.SetSPScrollMessage;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.ObjectHolder;

@EventBusSubscriber(modid = SurvivalPlus.MODID, bus = Bus.MOD)
public class SurvivalPlusContainer extends Container {
	public static final int WIDTH = 9;
	public static final int HEIGHT = 5;
	
	@ObjectHolder(SurvivalPlus.MODID + ":container")
	public static final ContainerType<SurvivalPlusContainer> TYPE = null;
	
	private static final ThreadLocal<Boolean> SUPPRESS_SEND_CHANGES = new ThreadLocal<>();
	
	@SubscribeEvent
	public static void onContainerRegistry(RegistryEvent.Register<ContainerType<?>> event) {
		ContainerType<SurvivalPlusContainer> type = new ContainerType<>(SurvivalPlusContainer::new);
		event.getRegistry().register(type.setRegistryName("container"));
		
		DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> ScreenManager.registerFactory(type, SurvivalPlusScreen::new));
	}
	
	public static StringTextComponent title() {
		return new StringTextComponent("SurvivalPlus");
	}
	
	public static class InfiniteInventory implements IInventory {
		private final PlayerEntity player;
		private List<ItemStack> items;
		
		private InfiniteInventory(PlayerEntity player, List<ItemStack> items) {
			this.player = player;
			this.items = items;
		}
		
		@Override
		public void clear() {
		}
		
		@Override
		public int getSizeInventory() {
			return this.items.size();
		}
		
		@Override
		public boolean isEmpty() {
			return this.items.isEmpty();
		}
		
		@Override
		public ItemStack getStackInSlot(int index) {
			if (index < this.items.size()) {
				ItemStack stack = this.items.get(index).copy();
				SPStackMarker.mark(stack);
				return stack;
			}
			return ItemStack.EMPTY;
		}
		
		@Override
		public ItemStack decrStackSize(int index, int count) {
			ItemStack stack = this.getStackInSlot(index);
			if (!stack.isEmpty()) {
				stack.setCount(count);
				return stack;
			}
			return ItemStack.EMPTY;
		}
		
		@Override
		public ItemStack removeStackFromSlot(int index) {
			return this.getStackInSlot(index);
		}
		
		@Override
		public void setInventorySlotContents(int index, ItemStack stack) {
		}
		
		@Override
		public void markDirty() {
		}
		
		@Override
		public boolean isUsableByPlayer(PlayerEntity player) {
			return true;
		}
	}
	
	public static class InfiniteSlot extends Slot {
		private int idxOffset;
		
		public InfiniteSlot(InfiniteInventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}
		
		@Override
		public boolean canTakeStack(PlayerEntity player) {
			return true;
		}
		
		@Override
		public boolean isItemValid(ItemStack stack) {
			// only allow items to be deleted if they are from the gui
			return SPStackMarker.isMarked(stack);
		}
		
		public void setScrollOffset(int offset) {
			this.idxOffset = offset * WIDTH;
		}
		
		@Override
		public void putStack(ItemStack stack) {
		}
		
		@Override
		public ItemStack decrStackSize(int amount) {
			return this.inventory.decrStackSize(this.getSlotIndex() + this.idxOffset, amount);
		}
		
		@Override
		public ItemStack getStack() {
			// we don't want to synchronize anything when running detectAndSendChanges, so hide our real state
			Boolean suppressSendChanges = SUPPRESS_SEND_CHANGES.get();
			if (suppressSendChanges != null && suppressSendChanges) {
				return ItemStack.EMPTY;
			}
			
			return this.inventory.getStackInSlot(this.getSlotIndex() + this.idxOffset);
		}
	}
	
	private final PlayerEntity player;
	public final InfiniteInventory inventory;
	
	private int scrollOffset;
	
	public SurvivalPlusContainer(int windowId, PlayerInventory playerInventory) {
		this(windowId, playerInventory, playerInventory.player);
	}
	
	public SurvivalPlusContainer(int windowId, PlayerInventory playerInventory, PlayerEntity player) {
		super(TYPE, windowId);
		this.player = player;
		
		List<ItemStack> buildingStacks = SPConfigs.SERVER.getFilter().getAllStacks();
		this.inventory = new InfiniteInventory(player, buildingStacks);
		
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				int i = x + y * WIDTH;
				addSlot(new InfiniteSlot(inventory, i, 9 + x * 18, 28 + 18 + y * 18));
			}
		}
		
		for (int h = 0; h < WIDTH; h++) {
			this.addSlot(new Slot(playerInventory, h, 9 + h * 18, 112 + 28));
		}
	}
	
	public void setScrollOffset(int scrollOffset) {
		scrollOffset = Math.max(scrollOffset, 0);
		
		if (this.scrollOffset != scrollOffset) {
			this.scrollOffset = scrollOffset;
			
			if (!player.world.isRemote) {
				SurvivalPlus.NETWORK.sendToServer(new SetSPScrollMessage(scrollOffset));
			}
			
			for (Slot slot : this.inventorySlots) {
				if (slot instanceof InfiniteSlot) {
					((InfiniteSlot) slot).setScrollOffset(scrollOffset);
				}
			}
		}
	}
	
	public int scrollHeight() {
		return (this.inventory.items.size() + WIDTH - 1) / WIDTH - HEIGHT;
	}
	
	public boolean canScroll() {
		return this.inventory.items.size() > WIDTH * HEIGHT;
	}
	
	@OnlyIn(Dist.CLIENT)
	@Override
	public void setAll(List<ItemStack> stacks) {
	}
	
	@Override
	public void detectAndSendChanges() {
		SUPPRESS_SEND_CHANGES.set(true);
		try {
			super.detectAndSendChanges();
		} finally {
			SUPPRESS_SEND_CHANGES.set(false);
		}
	}
	
	@Override
	public boolean canInteractWith(PlayerEntity playerIn) {
		return true;
	}
	
	@Override
	public ItemStack transferStackInSlot(PlayerEntity player, int index) {
		Slot slot = this.inventorySlots.get(index);
		
		// recreate shift-click to pick up max stack behaviour
		if (slot instanceof InfiniteSlot) {
			ItemStack stack = slot.getStack().copy();
			stack.setCount(stack.getMaxStackSize());
			player.inventory.setItemStack(stack);
			
			return ItemStack.EMPTY;
		}
		
		if (slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			if (index < 5 * 9) {
				stack.setCount(64);
				this.mergeItemStack(stack, 5 * 9, this.inventorySlots.size(), false);
				return ItemStack.EMPTY;
			} else {
				if (SPStackMarker.isMarked(stack)) {
					slot.putStack(ItemStack.EMPTY);
				}
				return ItemStack.EMPTY;
			}
		}
		
		return ItemStack.EMPTY;
	}
}
