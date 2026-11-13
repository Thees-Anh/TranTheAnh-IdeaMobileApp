begin;
alter table public.item_catalog add column description text not null default '' check(length(description)<=500);
alter table public.accessory_slots drop constraint accessory_slots_slot_check;
alter table public.accessory_slots add constraint accessory_slots_slot_check check(slot in ('HEAD','FACE','NECK','BACK'));
alter table public.pet_equipment drop constraint pet_equipment_slot_check;
alter table public.pet_equipment add constraint pet_equipment_slot_check check(slot in ('HEAD','FACE','NECK','BACK'));
alter table public.furniture_slots drop constraint furniture_slots_slot_check;
alter table public.furniture_slots add constraint furniture_slots_slot_check check(slot in ('BED','LAMP','CARPET','WINDOW','CLOCK','LIGHTS','BOOKSHELF','CAT_TREE','PLANT','TEA_TABLE','GRAMOPHONE'));
alter table public.room_furniture drop constraint room_furniture_slot_check;
alter table public.room_furniture add constraint room_furniture_slot_check check(slot in ('BED','LAMP','CARPET','WINDOW','CLOCK','LIGHTS','BOOKSHELF','CAT_TREE','PLANT','TEA_TABLE','GRAMOPHONE'));
insert into public.item_catalog(id,code,name,category,price_coins,asset_id,max_quantity,description) values
('e2300000-0000-0000-0000-000000000001','acc_crown','Vương Miện Vàng','ACCESSORY',150,'acc-crown',1,'Vương miện hoàng gia đính ngọc bích'),
('e2300000-0000-0000-0000-000000000002','acc_wizard_hat','Mũ Phù Thủy','ACCESSORY',140,'acc-wizard-hat',1,'Mũ tím huyền bí gắn ngôi sao vàng'),
('e2300000-0000-0000-0000-000000000003','acc_sprout','Mầm Cây & Hoa','ACCESSORY',80,'acc-sprout',1,'Mầm lá non rung rinh & kẹp hoa anh đào'),
('e2300000-0000-0000-0000-000000000004','acc_headphones','Tai Nghe Pastel','ACCESSORY',160,'acc-headphones',1,'Tai nghe chụp tai phát sáng êm ái'),
('e2300000-0000-0000-0000-000000000005','acc_glasses','Kính Ngôi Sao','ACCESSORY',110,'acc-glasses',1,'Kính mát hình ngôi sao lấp lánh'),
('e2300000-0000-0000-0000-000000000006','acc_monocle','Kính Quý Tộc','ACCESSORY',130,'acc-monocle',1,'Kính đơn dây xích vàng & bộ ria vểnh'),
('e2300000-0000-0000-0000-000000000007','acc_bowtie','Nơ Chuông Vàng','ACCESSORY',90,'acc-bowtie',1,'Vòng cổ nơ đỏ kèm chuông vàng lắc lư'),
('e2300000-0000-0000-0000-000000000008','acc_scarf','Khăn Len Ấm','ACCESSORY',95,'acc-scarf',1,'Khăn choàng len mùa đông kẻ sọc'),
('e2300000-0000-0000-0000-000000000009','acc_wings','Cánh Thiên Thần','ACCESSORY',200,'acc-wings',1,'Đôi cánh trắng muốt vẫy nhịp nhàng'),
('e2300000-0000-0000-0000-000000000010','acc_backpack','Balo & Bóng Bay','ACCESSORY',175,'acc-backpack',1,'Túi cà rốt đeo chéo & bóng bay trái tim'),
('e2300000-0000-0000-0000-000000000011','furn_window','Cửa Sổ Cầu Vồng','FURNITURE',220,'furn-window',1,'Cửa sổ vòm ngắm mây trôi & cầu vồng'),
('e2300000-0000-0000-0000-000000000012','furn_clock','Đồng Hồ Mèo','FURNITURE',150,'furn-clock',1,'Đồng hồ treo tường quả lắc đung đưa'),
('e2300000-0000-0000-0000-000000000013','furn_lights','Đèn LED Sao','FURNITURE',120,'furn-lights',1,'Dây đèn ngôi sao nhấp nháy ấm áp'),
('e2300000-0000-0000-0000-000000000014','furn_bookshelf','Kệ Sách Gỗ','FURNITURE',160,'furn-bookshelf',1,'Kệ sách pastel kèm chậu xương rồng'),
('e2300000-0000-0000-0000-000000000015','furn_cat_tree','Trụ Cào Móng','FURNITURE',280,'furn-cat-tree',1,'Nhà cây 3 tầng có bóng bông lắc lư'),
('e2300000-0000-0000-0000-000000000016','furn_bed','Đệm Vương Miện','FURNITURE',210,'furn-bed',1,'Đệm nhung hồng êm ái góc phòng'),
('e2300000-0000-0000-0000-000000000017','furn_plant','Chậu Cây Cười','FURNITURE',110,'furn-plant',1,'Cây Monstera trong chậu sứ mặt cười'),
('e2300000-0000-0000-0000-000000000018','furn_tea_table','Bàn Trà & Bánh','FURNITURE',190,'furn-tea-table',1,'Bàn gỗ thấp, ấm trà bốc khói & Macaron'),
('e2300000-0000-0000-0000-000000000019','furn_gramophone','Máy Đĩa Than','FURNITURE',250,'furn-gramophone',1,'Máy phát nhạc cổ điển bay nốt nhạc'),
('e2300000-0000-0000-0000-000000000020','furn_lamp','Đèn Hoa Linh Lan','FURNITURE',180,'furn-lamp',1,'Đèn cây hoa chuông tỏa hào quang');
insert into public.accessory_slots(item_id,slot) values
('e2300000-0000-0000-0000-000000000001','HEAD'),
('e2300000-0000-0000-0000-000000000002','HEAD'),
('e2300000-0000-0000-0000-000000000003','HEAD'),
('e2300000-0000-0000-0000-000000000004','HEAD'),
('e2300000-0000-0000-0000-000000000005','FACE'),
('e2300000-0000-0000-0000-000000000006','FACE'),
('e2300000-0000-0000-0000-000000000007','NECK'),
('e2300000-0000-0000-0000-000000000008','NECK'),
('e2300000-0000-0000-0000-000000000009','BACK'),
('e2300000-0000-0000-0000-000000000010','BACK');
insert into public.furniture_slots(item_id,slot) values
('e2300000-0000-0000-0000-000000000011','WINDOW'),
('e2300000-0000-0000-0000-000000000012','CLOCK'),
('e2300000-0000-0000-0000-000000000013','LIGHTS'),
('e2300000-0000-0000-0000-000000000014','BOOKSHELF'),
('e2300000-0000-0000-0000-000000000015','CAT_TREE'),
('e2300000-0000-0000-0000-000000000016','BED'),
('e2300000-0000-0000-0000-000000000017','PLANT'),
('e2300000-0000-0000-0000-000000000018','TEA_TABLE'),
('e2300000-0000-0000-0000-000000000019','GRAMOPHONE'),
('e2300000-0000-0000-0000-000000000020','LAMP');
create or replace function public.equipment_set(p_slot text,p_item_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); owned integer;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_slot is null or p_slot not in ('HEAD','FACE','NECK','BACK') then raise exception 'Invalid slot' using errcode='23514'; end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then raise exception 'Account unavailable' using errcode='23503'; end if;
    if p_item_id is null then
        delete from public.pet_equipment where user_id=actor and slot=p_slot;
    else
        perform 1 from public.accessory_slots s join public.item_catalog i on i.id=s.item_id
            where s.item_id=p_item_id and s.slot=p_slot and i.category='ACCESSORY' for share of s,i;
        if not found then raise exception 'Accessory does not fit slot' using errcode='23514'; end if;
        select quantity into owned from public.user_inventory where user_id=actor and item_id=p_item_id for update;
        if not found or owned<>1 then raise exception 'Accessory not owned' using errcode='42501'; end if;
        insert into public.pet_equipment(user_id,slot,item_id) values(actor,p_slot,p_item_id)
            on conflict(user_id,slot) do update set item_id=excluded.item_id,equipped_at=clock_timestamp()
            where public.pet_equipment.item_id<>excluded.item_id;
    end if;
    return public.equipment_state();
end $$;
create or replace function public.room_set(p_slot text,p_item_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); owned integer;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_slot is null or p_slot not in ('BED','LAMP','CARPET','WINDOW','CLOCK','LIGHTS','BOOKSHELF','CAT_TREE','PLANT','TEA_TABLE','GRAMOPHONE') then raise exception 'Invalid slot' using errcode='23514'; end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then raise exception 'Account unavailable' using errcode='23503'; end if;
    if p_item_id is null then
        delete from public.room_furniture where user_id=actor and slot=p_slot;
    else
        perform 1 from public.furniture_slots s join public.item_catalog i on i.id=s.item_id
            where s.item_id=p_item_id and s.slot=p_slot and i.category='FURNITURE' for share of s,i;
        if not found then raise exception 'Furniture does not fit slot' using errcode='23514'; end if;
        select quantity into owned from public.user_inventory where user_id=actor and item_id=p_item_id for update;
        if not found or owned<>1 then raise exception 'Furniture not owned' using errcode='42501'; end if;
        insert into public.room_furniture(user_id,slot,item_id) values(actor,p_slot,p_item_id)
            on conflict(user_id,slot) do update set item_id=excluded.item_id,placed_at=clock_timestamp()
            where public.room_furniture.item_id<>excluded.item_id;
    end if;
    return public.room_state();
end $$;
commit;
