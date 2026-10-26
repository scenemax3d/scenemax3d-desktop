//! Stateful list controls. Script commands and snapshots share one normalization path.
use super::*;
use scenemax_ui_input::Interaction;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct Snapshot {
    columns: usize,
    headers: Vec<String>,
    rows: Vec<Vec<String>>,
    widths: Vec<f32>,
    selected: i32,
    style: String,
    transparency: f32,
    header_font: Option<String>,
    row_font: Option<String>,
    header_size: f32,
    row_size: f32,
}
#[derive(Component)]
pub(crate) struct ListControl {
    state: Snapshot,
    width: f32,
    height: f32,
    scale: f32,
    visuals: Vec<Entity>,
    dirty: bool,
}
#[derive(Component)]
pub(crate) struct Row {
    owner: Entity,
    index: i32,
}

pub(crate) fn canonical(property: &str) -> Option<&'static str> {
    Some(match property.to_ascii_lowercase().as_str() {
        "columns" | "columncount" | "listcolumncount" => "columns",
        "headers" | "listheaders" => "headers",
        "rows" | "listrows" => "rows",
        "widths" | "columnwidths" | "listcolumnwidths" => "widths",
        "addrow" | "appendrow" => "addrow",
        "clear" | "clearrows" => "clearrows",
        "selected" | "selectedrow" | "selectedrowindex" => "selectedrow",
        "style" | "listviewstyle" => "style",
        "transparency" | "listviewtransparency" => "transparency",
        "headerfont" | "listheaderfont" | "listheaderfontname" => "headerfont",
        "rowfont" | "listrowfont" | "listrowfontname" => "rowfont",
        "headerfontsize" | "listheaderfontsize" => "headerfontsize",
        "rowfontsize" | "listrowfontsize" => "rowfontsize",
        _ => return None,
    })
}
fn cells(value: &str) -> Vec<String> {
    if value.is_empty() {
        vec![]
    } else {
        value.split('|').map(|s| s.trim().to_owned()).collect()
    }
}
impl Snapshot {
    fn normalize(&mut self) {
        self.columns = self.columns.clamp(1, 128);
        while self.headers.len() < self.columns {
            self.headers
                .push(format!("Column {}", self.headers.len() + 1));
        }
        self.headers.truncate(self.columns);
        self.widths.resize(self.columns, 100.);
        for row in &mut self.rows {
            row.resize(self.columns, String::new());
        }
    }
    pub(crate) fn valid(&self) -> bool {
        (1..=128).contains(&self.columns)
            && self.rows.len() <= 4096
            && self.headers.len() == self.columns
            && self.widths.len() == self.columns
            && self.rows.iter().all(|r| r.len() == self.columns)
            && self.widths.iter().all(|v| v.is_finite() && *v >= 1.)
            && self.transparency.is_finite()
            && (0. ..=100.).contains(&self.transparency)
            && [self.header_size, self.row_size]
                .into_iter()
                .all(|v| v.is_finite() && (1. ..=4096.).contains(&v))
    }
    fn change(&mut self, property: &str, value: &str) -> bool {
        let number = || value.trim().parse::<f32>().ok().filter(|n| n.is_finite());
        match property {
            "columns" => {
                let Some(n) = number() else {
                    return false;
                };
                self.columns = (n as usize).clamp(1, 128);
            }
            "headers" => self.headers = cells(value),
            "rows" => {
                self.rows = value
                    .split([';', '\n', '\r', '\u{0085}', '\u{2028}', '\u{2029}'])
                    .filter(|s| !s.trim().is_empty())
                    .map(cells)
                    .collect()
            }
            "widths" => {
                self.widths = value
                    .split(['|', ','])
                    .filter_map(|s| s.trim().parse::<f32>().ok())
                    .filter(|n| n.is_finite())
                    .map(|n| n.max(1.))
                    .collect()
            }
            "addrow" => self.rows.push(cells(value)),
            "clearrows" => {
                self.rows.clear();
                self.selected = -1;
            }
            "selectedrow" => {
                let Some(n) = number() else {
                    return false;
                };
                self.selected = n as i32;
            }
            "style" => self.style = value.to_owned(),
            "transparency" => {
                let Some(n) = number() else {
                    return false;
                };
                self.transparency = n.clamp(0., 100.);
            }
            "headerfont" | "rowfont" => {
                let font = (!matches!(
                    value.trim().to_ascii_lowercase().as_str(),
                    "" | "default" | "(default)"
                ))
                .then(|| value.to_owned());
                if property == "headerfont" {
                    self.header_font = font;
                } else {
                    self.row_font = font;
                }
            }
            "headerfontsize" | "rowfontsize" => {
                let Some(n) = number() else {
                    return false;
                };
                if property == "headerfontsize" {
                    self.header_size = n.max(1.);
                } else {
                    self.row_size = n.max(1.);
                }
            }
            _ => return false,
        }
        self.normalize();
        self.valid()
    }
}
pub(super) fn insert(
    commands: &mut Commands,
    entity: Entity,
    widget: &SceneMaxUiWidgetDef,
    width: f32,
    height: f32,
    scale: f32,
) {
    let mut state = Snapshot {
        columns: widget.list_column_count,
        headers: widget.list_headers.clone(),
        rows: widget.list_rows.clone(),
        widths: widget.list_column_widths.clone(),
        selected: widget.list_selected_row_index,
        style: widget.list_view_style.clone(),
        transparency: widget.list_view_transparency.clamp(0., 100.),
        header_font: widget.list_header_font_name.clone(),
        row_font: widget.list_row_font_name.clone(),
        header_size: widget.list_header_font_size.max(1.),
        row_size: widget.list_row_font_size.max(1.),
    };
    state.normalize();
    commands.entity(entity).insert(ListControl {
        state,
        width,
        height,
        scale,
        visuals: vec![],
        dirty: true,
    });
}
pub(crate) fn snapshot(world: &World, entity: Entity) -> Option<Snapshot> {
    Some(world.get::<ListControl>(entity)?.state.clone())
}
pub(super) fn apply(world: &mut World, entity: Entity, property: &str, value: &str) -> bool {
    let Some(mut list) = world.get_mut::<ListControl>(entity) else {
        return false;
    };
    let mut state = list.state.clone();
    if !state.change(property, value) {
        return false;
    }
    list.state = state;
    list.dirty = true;
    true
}
pub(super) fn restore(world: &mut World, entity: Entity, state: Snapshot) {
    if !state.valid() {
        return;
    }
    if let Some(mut list) = world.get_mut::<ListControl>(entity) {
        list.state = state;
        list.dirty = true;
    }
}

// The reference estimates line capacity from font size and column width.
// Materialize word/newline breaks for both shaped and bitmap font renderers.
fn wrap_cell(value: &str, size: f32, width: f32) -> String {
    let capacity = ((width - 8.).max(1.) / (size * 0.55).max(1.))
        .floor()
        .max(1.) as usize;
    let mut lines = Vec::new();
    for paragraph in value.replace("\r\n", "\n").replace('\r', "\n").split('\n') {
        let mut remaining: Vec<char> = paragraph.chars().collect();
        while remaining.len() > capacity {
            let cut = if remaining[capacity].is_whitespace() {
                capacity
            } else {
                remaining[..capacity]
                    .iter()
                    .rposition(|c| c.is_whitespace())
                    .filter(|i| *i > 0)
                    .unwrap_or(capacity)
            };
            lines.push(remaining[..cut].iter().collect::<String>());
            remaining.drain(..cut);
            while remaining.first().is_some_and(|c| c.is_whitespace()) {
                remaining.remove(0);
            }
        }
        lines.push(remaining.iter().collect());
    }
    lines.join("\n")
}
fn row_height(values: &[String], size: f32, widths: &[f32]) -> f32 {
    values
        .iter()
        .zip(widths)
        .map(|(text, width)| {
            wrap_cell(text, size, *width).split('\n').count() as f32 * size * 1.25 + 8.
        })
        .fold(size * 1.6 + 8., f32::max)
}

pub(crate) fn render(
    mut commands: Commands,
    server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    mut runtime: ResMut<SceneMaxUiRuntime>,
    mut lists: Query<(Entity, &mut ListControl, &SceneMaxUiWidget)>,
) {
    for (owner, mut list, marker) in &mut lists {
        if !list.dirty {
            continue;
        }
        list.dirty = false;
        for visual in list.visuals.drain(..) {
            commands.entity(visual).try_despawn();
        }
        let s = list.state.clone();
        if std::env::var_os("SCENEMAX_ATTACHMENT_AUDIT").is_some() {
            write_runtime_diagnostic_line(format!(
                "UI_LIST_STATE {}.{}.{} {}",
                marker.ui_name,
                marker.layer,
                marker.widget_path.join("."),
                serde_json::to_string(&s).unwrap_or_default()
            ));
        }
        let palette = match s.style.to_ascii_lowercase().as_str() {
            "dark" => [
                "#1F232A", "#323945", "#252B34", "#20252D", "#345B8C", "#FFFFFF", "#E7ECF2",
            ],
            "blue" => [
                "#F4F8FF", "#CFE2FF", "#EAF2FF", "#DDEBFF", "#9CC3FF", "#113A6B", "#17314F",
            ],
            _ => [
                "#FAFAFA", "#E2E6EA", "#FFFFFF", "#F2F4F6", "#D4E7FF", "#20242A", "#30343A",
            ],
        };
        let color = |i: usize| parse_ui_color(palette[i]).with_alpha(1. - s.transparency / 100.);
        commands.entity(owner).insert(BackgroundColor(color(0)));
        let total: f32 = s.widths.iter().sum();
        let widths: Vec<f32> = s.widths.iter().map(|w| w / total * list.width).collect();
        let mut y = 0.;
        for (index, values) in std::iter::once(&s.headers).chain(s.rows.iter()).enumerate() {
            let header = index == 0;
            let size = if header { s.header_size } else { s.row_size };
            let height = row_height(values, scaled_font_size(size, list.scale), &widths);
            if y + height > list.height {
                break;
            }
            let row = commands
                .spawn((
                    Node {
                        position_type: PositionType::Absolute,
                        top: Val::Px(y),
                        width: Val::Px(list.width),
                        height: Val::Px(height),
                        ..default()
                    },
                    BackgroundColor(color(if header {
                        1
                    } else if s.selected == index as i32 - 1 {
                        4
                    } else if index % 2 == 1 {
                        2
                    } else {
                        3
                    })),
                    Interaction::default(),
                    Row {
                        owner,
                        index: index as i32 - 1,
                    },
                ))
                .id();
            add_child_if_alive(&mut commands, owner, row);
            list.visuals.push(row);
            let mut x = 0.;
            for (value, width) in values.iter().zip(&widths) {
                let cell = spawn_scenemax_ui_text_entity(
                    &mut commands,
                    &server,
                    &context,
                    &mut runtime,
                    "SceneMax list cell".into(),
                    Node {
                        position_type: PositionType::Absolute,
                        left: Val::Px(x + 4.),
                        top: Val::Px(4.),
                        width: Val::Px((width - 8.).max(1.)),
                        height: Val::Px(height - 8.),
                        overflow: Overflow::clip(),
                        ..default()
                    },
                    Visibility::Inherited,
                    ZIndex(0),
                    wrap_cell(value, scaled_font_size(size, list.scale), *width),
                    if header {
                        s.header_font.as_deref()
                    } else {
                        s.row_font.as_deref()
                    },
                    size,
                    color(if header { 5 } else { 6 }),
                    if header {
                        Justify::Center
                    } else {
                        Justify::Left
                    },
                    list.scale,
                    (width - 8.).max(1.),
                    height - 8.,
                    None,
                );
                commands.entity(cell).insert(bevy::ui::FocusPolicy::Pass);
                add_child_if_alive(&mut commands, row, cell);
                x += width;
            }
            y += height;
        }
    }
}

pub(crate) fn select(
    mut commands: Commands,
    rows: Query<(&Row, &Interaction), Changed<Interaction>>,
    mut lists: Query<&mut ListControl>,
    runtime: Res<SceneMaxUiRuntime>,
) {
    for (row, interaction) in &rows {
        if *interaction != Interaction::Pressed || row.index < 0 {
            continue;
        }
        let Ok(mut list) = lists.get_mut(row.owner) else {
            continue;
        };
        list.state.selected = row.index;
        list.dirty = true;
        for loaded in runtime.loaded.values() {
            for (path, target) in &loaded.targets {
                if target.entity == row.owner && target.multiplayer {
                    let path = path.clone();
                    let entity = row.owner;
                    commands.queue(move |world: &mut World| {
                        multiplayer::widgets::capture_list(world, entity, &path)
                    });
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn arrow_handlers_change_the_live_list_selection() {
        use bevy::ecs::system::RunSystemOnce;
        let source = "when key down is pressed once do\nrow = row + 1\nif (row >= sessions.Length) {\nrow = sessions.Length - 1\n}\nUI.layer1.rooms.selected = row\nend do\nwhen key up is pressed once do\nrow = row - 1\nif (row < 0) {\nrow = 0\n}\nUI.layer1.rooms.selected = row\nend do";
        let mut world = crate::input_gameplay_tests::world(source);
        crate::value_calls::tests::run_in(
            &mut world,
            "var row = 0 [0..1];\nvar sessions = [\"First\", \"Second\"]",
        );
        let mut assets = App::new();
        assets.add_plugins((MinimalPlugins, bevy::asset::AssetPlugin::default()));
        world.insert_resource(assets.world().resource::<AssetServer>().clone());
        world.insert_resource(SceneMaxLaunchContext {
            script_root: None,
            asset_root: None,
            builtin_asset_root: None,
            window_width: 800,
            window_height: 900,
        });
        let widget: SceneMaxUiWidgetDef = serde_json::from_str(r#"{"name":"rooms","type":"LIST_VIEW","listRows":[["First"],["Second"]],"listSelectedRowIndex":0}"#).unwrap();
        let entity = world.spawn_empty().id();
        insert(&mut world.commands(), entity, &widget, 600., 300., 1.);
        world.flush();
        let mut loaded = LoadedSceneMaxUi::default();
        loaded.targets.insert(
            target_key("lobby", "layer1", &["rooms".into()]),
            SceneMaxUiTarget {
                entity,
                text_entity: None,
                multiplayer: false,
                widget_type: "LIST_VIEW".into(),
                ui_scale: 1.,
            },
        );
        let mut runtime = SceneMaxUiRuntime::default();
        runtime.active_ui_name = Some("lobby".into());
        runtime.loaded.insert("lobby".into(), loaded);
        world.insert_resource(runtime);
        for (key, expected) in [
            (KeyCode::ArrowUp, 0),
            (KeyCode::ArrowDown, 1),
            (KeyCode::ArrowDown, 1),
            (KeyCode::ArrowUp, 0),
            (KeyCode::ArrowUp, 0),
        ] {
            world.resource_mut::<ButtonInput<KeyCode>>().press(key);
            crate::input_gameplay_tests::input(&mut world);
            world.run_system_once(apply_scenemax_ui_actions).unwrap();
            assert_eq!(snapshot(&world, entity).unwrap().selected, expected);
            assert!(world.get::<ListControl>(entity).unwrap().dirty);
            world.resource_mut::<ButtonInput<KeyCode>>().release(key);
            world.resource_mut::<ButtonInput<KeyCode>>().clear();
        }
    }

    #[test]
    fn wrapped_rows_preserve_paragraphs_and_use_tallest_cell() {
        assert_eq!(
            wrap_cell("alpha beta gamma", 20., 118.),
            "alpha beta\ngamma"
        );
        assert_eq!(wrap_cell("one\n\nend", 20., 118.), "one\n\nend");
        assert_eq!(
            row_height(&["short".into(), "a\nb\nc".into()], 20., &[118., 118.]),
            83.
        );
        assert_eq!(row_height(&["short".into()], 20., &[118.]), 40.);
    }
    #[test]
    fn list_commands_normalize_data_and_snapshot_replay_is_idempotent() {
        let widget: SceneMaxUiWidgetDef =
            serde_json::from_str(r#"{"name":"table","type":"LIST_VIEW"}"#).unwrap();
        let mut world = World::new();
        let entity = world.spawn_empty().id();
        insert(&mut world.commands(), entity, &widget, 600., 300., 1.);
        world.flush();
        for (property, value) in [
            ("columns", "2"),
            ("headers", "Name | Score"),
            ("rows", "First|10;Second|20"),
            ("widths", "3,1"),
            ("addrow", "Third|30"),
            ("selectedrow", "2"),
            ("style", "dark"),
            ("transparency", "25"),
            ("headerfont", "Title"),
            ("rowfont", "Body"),
            ("headerfontsize", "26"),
            ("rowfontsize", "19"),
        ] {
            assert!(apply(&mut world, entity, property, value), "{property}");
        }
        let state = snapshot(&world, entity).unwrap();
        assert_eq!(state.rows.len(), 3);
        assert_eq!(state.selected, 2);
        assert_eq!(state.widths, vec![3., 1.]);
        let encoded = serde_json::to_string(&state).unwrap();
        assert!(
            scenemax_multiplayer::protocol::encode_value(&serde_json::Value::String(
                encoded.clone()
            ))
            .is_err()
        ); // requires coherent multipart transport
        for _ in 0..2 {
            restore(&mut world, entity, serde_json::from_str(&encoded).unwrap());
        }
        assert_eq!(snapshot(&world, entity).unwrap().rows.len(), 3);
        assert!(!apply(&mut world, entity, "headerfontsize", "NaN"));
        assert_eq!(snapshot(&world, entity).unwrap().header_size, 26.);
        assert!(apply(&mut world, entity, "clearrows", ""));
        let state = snapshot(&world, entity).unwrap();
        assert!(state.rows.is_empty());
        assert_eq!(state.selected, -1);
        assert!(apply(&mut world, entity, "columns", "3"));
        assert_eq!(
            snapshot(&world, entity).unwrap().headers,
            vec!["Name", "Score", "Column 3"]
        );
    }
}
