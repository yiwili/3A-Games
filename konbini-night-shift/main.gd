extends Node3D

const FLOOR_SIZE := Vector3(24.0, 0.2, 16.0)
const PLAYER_SPEED := 5.0
const OPEN_HOUR := 22.0
const NPC_ROUTE := [Vector3(8.7, 0.0, 4.0), Vector3(8.7, 0.0, -5.0), Vector3(-8.5, 0.0, -5.0), Vector3(-8.5, 0.0, 4.0)]

var player: CharacterBody3D
var camera: Camera3D
var clerk: CharacterBody3D
var clock_label: Label
var status_label: Label
var prompt_label: Label
var shop_time := OPEN_HOUR
var yaw := 0.0
var pitch := -0.15
var clerk_target := 1
var clerk_phase := 0.0
var interaction_text := ""
var mats: Dictionary = {}

func _ready() -> void:
	_build_materials()
	_build_environment()
	_build_store()
	_build_player()
	_build_clerk()
	_build_rain()
	_build_ui()
	Input.mouse_mode = Input.MOUSE_MODE_CAPTURED

func _process(delta: float) -> void:
	shop_time = fmod(shop_time + delta * 0.08, 24.0)
	clerk_phase += delta
	_update_clock()
	_update_lighting()
	_update_clerk(delta)
	_update_interaction()

func _physics_process(_delta: float) -> void:
	if not player:
		return
	var input_vec := Input.get_vector("move_left", "move_right", "move_forward", "move_back")
	var direction := (player.transform.basis * Vector3(input_vec.x, 0.0, input_vec.y)).normalized()
	player.velocity.x = direction.x * PLAYER_SPEED
	player.velocity.z = direction.z * PLAYER_SPEED
	player.velocity.y = 0.0
	player.move_and_slide()
	player.position.x = clamp(player.position.x, -10.5, 10.5)
	player.position.z = clamp(player.position.z, -6.5, 6.5)

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		yaw -= event.relative.x * 0.0025
		pitch = clamp(pitch - event.relative.y * 0.0025, -1.0, 0.65)
		player.rotation.y = yaw
		camera.rotation.x = pitch
	elif event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
	elif event is InputEventKey and event.pressed and event.keycode == KEY_E:
		if interaction_text != "":
			status_label.text = "已完成交互：%s" % interaction_text
	elif event is InputEventMouseButton and event.pressed:
		Input.mouse_mode = Input.MOUSE_MODE_CAPTURED

func _build_materials() -> void:
	var tile_color: Texture2D = _load_image_texture("res://assets/ambientcg/Tiles074_1K-JPG_Color.jpg")
	var tile_normal: Texture2D = _load_image_texture("res://assets/ambientcg/Tiles074_1K-JPG_NormalGL.jpg")
	var tile_roughness: Texture2D = _load_image_texture("res://assets/ambientcg/Tiles074_1K-JPG_Roughness.jpg")
	mats.floor = _mat(Color("#8c929a"), 0.8, 0.0, 0.0, tile_color)
	mats.floor.normal_enabled = true
	mats.floor.normal_texture = tile_normal
	mats.floor.roughness_texture = tile_roughness
	mats.floor.uv1_scale = Vector3(4.0, 4.0, 4.0)
	mats.wall = _mat(Color("#f4eee2"), 0.72, 0.0, 0.0, _pattern_texture(Color("#f4eee2"), Color("#d6ccb9"), 16))
	mats.trim = _mat(Color("#d93045"), 0.35)
	mats.dark = _mat(Color("#20242b"), 0.25, 0.15, 0.0, _pattern_texture(Color("#20242b"), Color("#39414b"), 5))
	mats.metal = _mat(Color("#9aa3ad"), 0.15, 0.85)
	mats.glass = _mat(Color("#a9e3f2"), 0.05, 0.1, 0.35)
	mats.cola = _mat(Color("#b8292e"), 0.35, 0.0, 0.0, _pattern_texture(Color("#b8292e"), Color("#f0c04a"), 4))
	mats.water = _mat(Color("#76b8d8"), 0.08, 0.0, 0.4)
	mats.snack = _mat(Color("#f2b84b"), 0.4, 0.0, 0.0, _pattern_texture(Color("#f2b84b"), Color("#e87c3e"), 5))
	mats.green = _mat(Color("#63a85d"), 0.5)
	mats.white = _mat(Color("#fff8e7"))
	mats.neon = _mat(Color("#ff6683"), 0.1, 0.0, 1.8)
	mats.uniform = _mat(Color("#31588e"), 0.5)
	mats.hair = _mat(Color("#241d2a"), 0.8)
	mats.skin = _mat(Color("#ffd0b5"), 0.8)
	mats.skirt = _mat(Color("#26314c"), 0.65)

func _build_environment() -> void:
	var world := WorldEnvironment.new()
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color("#070b16")
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color("#6682bb")
	environment.ambient_light_energy = 0.45
	environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	world.environment = environment
	add_child(world)
	var moon := DirectionalLight3D.new()
	moon.rotation_degrees = Vector3(-55, -25, 0)
	moon.light_color = Color("#91a9e8")
	moon.light_energy = 0.7
	moon.shadow_enabled = true
	add_child(moon)

func _build_store() -> void:
	_box("Floor", FLOOR_SIZE, Vector3(0, -0.1, 0), mats.floor)
	_box("BackWall", Vector3(24, 5, 0.25), Vector3(0, 2.5, -7.8), mats.wall)
	_box("LeftWall", Vector3(0.25, 5, 16), Vector3(-12, 2.5, 0), mats.wall)
	_box("RightWall", Vector3(0.25, 5, 16), Vector3(12, 2.5, 0), mats.wall)
	_box("Roof", Vector3(24, 0.25, 16), Vector3(0, 5, 0), mats.dark)
	_build_front_windows()
	_build_sign()
	_build_shelves()
	_build_counter()
	_build_checkout_light()

func _build_front_windows() -> void:
	for x in [-8.0, -4.0, 4.0, 8.0]:
		_box("RainWindow", Vector3(3.4, 3.2, 0.12), Vector3(x, 2.5, 7.85), mats.glass)
		_box("WindowFrame", Vector3(0.12, 3.5, 0.18), Vector3(x - 1.75, 2.5, 7.72), mats.dark)
		_box("WindowFrame", Vector3(0.12, 3.5, 0.18), Vector3(x + 1.75, 2.5, 7.72), mats.dark)
		_box("RainReflection", Vector3(2.6, 0.05, 0.03), Vector3(x, 1.1, 7.7), mats.water, false)
	_box("EntranceLeft", Vector3(1.7, 3.6, 0.18), Vector3(-1.7, 2.1, 7.75), mats.glass)
	_box("EntranceRight", Vector3(1.7, 3.6, 0.18), Vector3(1.7, 2.1, 7.75), mats.glass)
	_box("DoorHeader", Vector3(4.0, 0.22, 0.25), Vector3(0, 4.0, 7.7), mats.trim)

func _build_sign() -> void:
	_box("SignBoard", Vector3(10, 1.5, 0.3), Vector3(0, 4.4, 7.6), mats.trim)
	_box("SignGlow", Vector3(8.5, 0.85, 0.12), Vector3(0, 4.4, 7.4), mats.neon, false)
	var sign_light := OmniLight3D.new()
	sign_light.position = Vector3(0, 4.0, 6.8)
	sign_light.light_color = Color("#ff4868")
	sign_light.light_energy = 2.0
	sign_light.omni_range = 10.0
	add_child(sign_light)

func _build_shelves() -> void:
	for z in [-4.5, -1.8, 1.0]:
		_shelf(Vector3(-6.5, 0, z), "饮料 / Drinks")
		_shelf(Vector3(0.0, 0, z), "零食 / Snacks")
		_shelf(Vector3(6.0, 0, z), "日用品 / Daily")

func _shelf(at: Vector3, category: String) -> void:
	_box("ShelfCollision", Vector3(3.7, 2.5, 0.75), at + Vector3(0, 1.25, 0), mats.metal)
	for y in [0.35, 1.0, 1.65, 2.3]:
		_box("ShelfBoard", Vector3(3.5, 0.08, 1.0), at + Vector3(0, y, 0), mats.dark)
		for x in [-1.25, -0.75, -0.25, 0.25, 0.75, 1.25]:
			var product_mat: Material = mats.cola if category.begins_with("饮") else mats.snack if category.begins_with("零") else mats.green
			_box("Product", Vector3(0.25, 0.38, 0.25), at + Vector3(x, y + 0.22, 0.05), product_mat, false)
	_box("CategoryPlate", Vector3(2.4, 0.22, 0.05), at + Vector3(0, 2.85, -0.42), mats.white, false)

func _build_counter() -> void:
	_box("Counter", Vector3(5.5, 1.2, 1.4), Vector3(8.4, 0.6, 5.0), mats.trim)
	_box("CounterTop", Vector3(5.7, 0.18, 1.55), Vector3(8.4, 1.28, 5.0), mats.dark)
	_box("Register", Vector3(0.9, 0.65, 0.8), Vector3(7.7, 1.65, 4.8), mats.metal, false)
	_box("RegisterScreen", Vector3(0.55, 0.38, 0.05), Vector3(7.7, 2.05, 4.38), mats.neon, false)
	_box("HotFoodCase", Vector3(3.0, 1.3, 0.8), Vector3(9.7, 0.65, 3.8), mats.glass)
	for x in [9.0, 9.5, 10.0, 10.5]:
		_box("Bento", Vector3(0.35, 0.12, 0.42), Vector3(x, 1.38, 3.8), mats.snack, false)
	var interaction := Area3D.new()
	interaction.name = "CheckoutInteraction"
	interaction.position = Vector3(7.7, 1.1, 3.8)
	interaction.set_meta("interaction_text", "收银机：今晚营业额 + 1280 円")
	var shape := CollisionShape3D.new()
	var sphere := SphereShape3D.new()
	sphere.radius = 1.5
	shape.shape = sphere
	interaction.add_child(shape)
	add_child(interaction)

func _build_checkout_light() -> void:
	var light := OmniLight3D.new()
	light.position = Vector3(8.0, 3.8, 4.0)
	light.light_color = Color("#ffe4b2")
	light.light_energy = 3.5
	light.omni_range = 8.0
	add_child(light)

func _build_player() -> void:
	player = CharacterBody3D.new()
	player.name = "Player"
	player.position = Vector3(0, 1.0, 5.5)
	add_child(player)
	var collision := CollisionShape3D.new()
	var capsule := CapsuleShape3D.new()
	capsule.height = 1.8
	capsule.radius = 0.35
	collision.shape = capsule
	player.add_child(collision)
	camera = Camera3D.new()
	camera.position = Vector3(0, 0.65, 0)
	camera.current = true
	player.add_child(camera)

func _build_clerk() -> void:
	clerk = CharacterBody3D.new()
	clerk.name = "AfterSchoolClerk"
	clerk.position = NPC_ROUTE[0] + Vector3(0, 0.95, 0)
	add_child(clerk)
	var collision := CollisionShape3D.new()
	var capsule := CapsuleShape3D.new()
	capsule.height = 1.7
	capsule.radius = 0.3
	collision.shape = capsule
	clerk.add_child(collision)
	_capsule("ClerkBody", 0.32, 0.8, Vector3(0, -0.1, 0), mats.uniform, clerk)
	_capsule("ClerkSkirt", 0.4, 0.45, Vector3(0, -0.65, 0), mats.skirt, clerk)
	_sphere("ClerkHead", 0.32, Vector3(0, 0.55, 0), mats.skin, clerk)
	_sphere("ClerkHair", 0.35, Vector3(0, 0.72, -0.06), mats.hair, clerk)
	_capsule("ClerkHairBack", 0.16, 0.5, Vector3(0, 0.3, -0.23), mats.hair, clerk)
	_box_child("Apron", Vector3(0.45, 0.52, 0.04), Vector3(0, -0.15, 0.32), mats.white, clerk)
	_box_child("NameTag", Vector3(0.15, 0.08, 0.03), Vector3(0.12, 0.02, 0.35), mats.neon, clerk)
	var tag := Label3D.new()
	tag.text = "さくら"
	tag.position = Vector3(0, 1.25, 0)
	tag.font_size = 24
	tag.modulate = Color("#ffd6df")
	clerk.add_child(tag)

func _update_clerk(delta: float) -> void:
	var target: Vector3 = NPC_ROUTE[clerk_target]
	var flat_target := Vector3(target.x, clerk.position.y, target.z)
	var offset := flat_target - clerk.position
	if offset.length() < 0.35:
		clerk_target = (clerk_target + 1) % NPC_ROUTE.size()
	else:
		clerk.velocity = offset.normalized() * 1.15
		clerk.move_and_slide()
		var look_direction := Vector3(clerk.velocity.x, 0, clerk.velocity.z)
		if look_direction.length_squared() > 0.001:
			clerk.look_at(clerk.position + look_direction, Vector3.UP)
	var step := sin(clerk_phase * 10.0) * 0.06
	for child in clerk.get_children():
		if child.name == "ClerkBody":
			child.position.y = -0.1 + abs(step)

func _build_rain() -> void:
	for x in [-8.0, -4.0, 4.0, 8.0]:
		var rain := GPUParticles3D.new()
		rain.name = "RainOutside"
		rain.position = Vector3(x, 6.0, 8.8)
		rain.amount = 140
		rain.lifetime = 1.4
		rain.randomness = 0.35
		rain.visibility_aabb = AABB(Vector3(-2, -6, -0.2), Vector3(4, 12, 0.4))
		var process := ParticleProcessMaterial.new()
		process.direction = Vector3(0.05, -1.0, -0.05)
		process.initial_velocity_min = 8.0
		process.initial_velocity_max = 12.0
		process.gravity = Vector3(0, -2, 0)
		process.emission_shape = ParticleProcessMaterial.EMISSION_SHAPE_BOX
		process.emission_box_extents = Vector3(1.7, 0.1, 0.1)
		rain.process_material = process
		var drop := QuadMesh.new()
		drop.size = Vector2(0.018, 0.32)
		drop.material = mats.water
		rain.draw_pass_1 = drop
		add_child(rain)
		_box("WetPavement", Vector3(3.2, 0.02, 0.35), Vector3(x, 0.02, 8.05), mats.water, false)

func _build_ui() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	clock_label = Label.new()
	clock_label.position = Vector2(28, 24)
	clock_label.add_theme_font_size_override("font_size", 28)
	layer.add_child(clock_label)
	status_label = Label.new()
	status_label.position = Vector2(28, 62)
	status_label.text = "夜班营业中  |  WASD 移动 · 鼠标环顾 · E 交互 · ESC 释放鼠标"
	status_label.add_theme_font_size_override("font_size", 16)
	layer.add_child(status_label)
	prompt_label = Label.new()
	prompt_label.position = Vector2(28, 100)
	prompt_label.add_theme_color_override("font_color", Color("#ffe29b"))
	prompt_label.add_theme_font_size_override("font_size", 18)
	layer.add_child(prompt_label)
	var title := Label.new()
	title.position = Vector2(28, 665)
	title.text = "深夜便利店：雨夜营业中。放课后兼职店员正在巡店。"
	title.add_theme_color_override("font_color", Color("#ffb7c5"))
	title.add_theme_font_size_override("font_size", 18)
	layer.add_child(title)
	_update_clock()

func _update_interaction() -> void:
	interaction_text = ""
	var query := PhysicsRayQueryParameters3D.create(camera.global_position, camera.global_position + -camera.global_transform.basis.z * 3.0)
	query.exclude = [player]
	var hit := get_world_3d().direct_space_state.intersect_ray(query)
	if hit and hit.collider is Area3D and hit.collider.has_meta("interaction_text"):
		interaction_text = hit.collider.get_meta("interaction_text")
		prompt_label.text = "[E] " + interaction_text
	else:
		prompt_label.text = ""

func _update_clock() -> void:
	if clock_label:
		clock_label.text = "KONBINI  |  %02d:%02d" % [int(shop_time), int((shop_time - int(shop_time)) * 60)]

func _update_lighting() -> void:
	var night_factor := 0.7 + sin(shop_time / 24.0 * TAU) * 0.15
	if status_label:
		status_label.modulate = Color(1.0, night_factor, night_factor)

func _mat(color: Color, roughness := 0.55, metallic := 0.0, emission_energy := 0.0, texture: Texture2D = null) -> StandardMaterial3D:
	var material := StandardMaterial3D.new()
	material.albedo_color = color
	material.roughness = roughness
	material.metallic = metallic
	material.albedo_texture = texture
	if emission_energy > 0.0:
		material.emission_enabled = true
		material.emission = color
		material.emission_energy_multiplier = emission_energy
	return material

func _pattern_texture(a: Color, b: Color, size: int) -> ImageTexture:
	var image := Image.create(size, size, false, Image.FORMAT_RGBA8)
	for y in size:
		for x in size:
			var stripe := ((x / 2) + (y / 2)) % 2 == 0
			image.set_pixel(x, y, a if stripe else b)
	return ImageTexture.create_from_image(image)

func _load_image_texture(path: String) -> Texture2D:
	var texture := load(path) as Texture2D
	if texture == null:
		push_error("无法加载贴图: " + path)
	return texture

func _box(label: String, size: Vector3, at: Vector3, material: Material, collision := true) -> MeshInstance3D:
	var mesh_instance := MeshInstance3D.new()
	mesh_instance.name = label
	var mesh := BoxMesh.new()
	mesh.size = size
	mesh_instance.mesh = mesh
	mesh_instance.position = at
	mesh_instance.material_override = material
	add_child(mesh_instance)
	if collision:
		var body := StaticBody3D.new()
		body.name = label + "Collision"
		body.position = at
		var shape := CollisionShape3D.new()
		var box := BoxShape3D.new()
		box.size = size
		shape.shape = box
		body.add_child(shape)
		add_child(body)
	return mesh_instance

func _capsule(label: String, radius: float, height: float, at: Vector3, material: Material, parent: Node3D) -> void:
	var mesh := CapsuleMesh.new()
	mesh.radius = radius
	mesh.height = height
	var instance := MeshInstance3D.new()
	instance.name = label
	instance.mesh = mesh
	instance.material_override = material
	instance.position = at
	parent.add_child(instance)

func _sphere(label: String, radius: float, at: Vector3, material: Material, parent: Node3D) -> void:
	var mesh := SphereMesh.new()
	mesh.radius = radius
	mesh.height = radius * 2.0
	var instance := MeshInstance3D.new()
	instance.name = label
	instance.mesh = mesh
	instance.material_override = material
	instance.position = at
	parent.add_child(instance)

func _box_child(label: String, size: Vector3, at: Vector3, material: Material, parent: Node3D) -> void:
	var mesh := BoxMesh.new()
	mesh.size = size
	var instance := MeshInstance3D.new()
	instance.name = label
	instance.mesh = mesh
	instance.material_override = material
	instance.position = at
	parent.add_child(instance)
