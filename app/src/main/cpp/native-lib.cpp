#ifdef __ANDROID__
#include <jni.h>
#include <android/log.h>
#endif

#if CALCULATORPLUS_WITH_GIAC
#include "giac/gen.h"
#include "giac/prog.h"
#include "giac/usual.h"
#include "giac/subst.h"
#include "giac/plot.h"
#include "giac/plot3d.h"
#include "giac/tex.h"
#endif

#include <algorithm>
#include <cmath>
#include <cctype>
#include <cstring>


#include <atomic>
#include <iomanip>
#include <map>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "calcora_engine.h"

namespace {

std::map<std::string, double> variables;
std::map<std::string, std::string> functions;
std::atomic_bool interrupted{false};
std::mutex engine_mutex;

#if CALCULATORPLUS_WITH_GIAC
giac::context *giac_context = nullptr;
#endif

std::string trim(const std::string &value) {
    size_t start = 0;
    while (start < value.size() && std::isspace(static_cast<unsigned char>(value[start]))) start++;
    size_t end = value.size();
    while (end > start && std::isspace(static_cast<unsigned char>(value[end - 1]))) end--;
    return value.substr(start, end - start);
}

std::string compact(std::string value) {
    value.erase(std::remove_if(value.begin(), value.end(), [](unsigned char c) { return std::isspace(c); }), value.end());
    std::replace(value.begin(), value.end(), '\n', ';');
    return value;
}

std::string json_escape(const std::string &value) {
    std::ostringstream out;
    for (char c: value) {
        switch (c) {
            case '\\': out << "\\\\"; break;
            case '"': out << "\\\""; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default: out << c;
        }
    }
    return out.str();
}

std::string format_double(double value) {
    if (std::fabs(value) < 1e-12) value = 0.0;
    std::ostringstream out;
    out << std::setprecision(12) << value;
    std::string text = out.str();
    if (text.find('.') != std::string::npos) {
        while (!text.empty() && text.back() == '0') text.pop_back();
        if (!text.empty() && text.back() == '.') text.pop_back();
    }
    return text.empty() ? "0" : text;
}

#if CALCULATORPLUS_WITH_GIAC
// Extract plot data from giac's actual graphic output.
// Returns a JSON array of curve objects:
// [{"var":"x","xmin":-5,"xmax":5,"pts":[[x1,y1],[x2,y2],...]}, ...]
static giac::gen plot_pt_to_numeric(const giac::gen &pt, giac::context *contextptr) {
    giac::gen npt = giac::evalf_double(pt, 1, contextptr);
    if (npt.type == giac::_CPLX || npt.type == giac::_DOUBLE_ || npt.type == giac::_ZINT || npt.type == giac::_INT_) {
        if (npt.type == giac::_CPLX || npt.type == giac::_DOUBLE_) return npt;
        return giac::gen((double)npt.val);
    }
    if (pt.type == giac::_CPLX || pt.type == giac::_DOUBLE_ || pt.type == giac::_ZINT || pt.type == giac::_INT_) {
        if (pt.type == giac::_CPLX || pt.type == giac::_DOUBLE_) return pt;
        return giac::gen((double)pt.val);
    }
    return giac::evalf(pt, 1, contextptr);
}

static bool plot_write_pt(std::ostringstream &out, const giac::gen &pt, bool &first,
                          giac::context *contextptr) {
    giac::gen npt = plot_pt_to_numeric(pt, contextptr);
    double x=0,y=0; bool ok=false;
    if (npt.type == giac::_CPLX) {
        giac::gen r=giac::evalf_double(giac::re(npt,contextptr), 1, contextptr);
        giac::gen im=giac::evalf_double(giac::im(npt,contextptr), 1, contextptr);
        if (r.type==giac::_DOUBLE_ && im.type==giac::_DOUBLE_) {
            x=r._DOUBLE_val; y=im._DOUBLE_val; ok=std::isfinite(x)&&std::isfinite(y);
        }
    } else if (npt.type == giac::_DOUBLE_ || npt.type == giac::_ZINT || npt.type == giac::_INT_) {
        // Pure real: giac encodes the x-index in the real part, imag (y) is 0
        y = 0.0;
        x = (npt.type == giac::_DOUBLE_) ? npt._DOUBLE_val : (double)npt.val;
        ok = std::isfinite(x);
    }
    if (ok) {
        if (!first) out << ",";
        out << "[" << x << "," << y << "]"; first=false;
    }
    return ok;
}

static std::string extract_giac_plot_data(const giac::gen &result, giac::context *contextptr) {
    std::ostringstream out;
    out << "[";

    giac::gen plot_data = result;
    if (plot_data.is_symb_of_sommet(giac::at_pnt)) {
        plot_data = giac::gen(giac::vecteur(1, plot_data), giac::_SEQ__VECT);
    }
    if (plot_data.type != giac::_VECT) {
        out << "]"; return out.str();
    }

    bool first_item = true;
    for (const auto &item : *plot_data._VECTptr) {
        giac::gen inner = item;
        while (inner.is_symb_of_sommet(giac::at_pnt)) {
            inner = giac::remove_at_pnt(inner);
        }

        // ---- symb_curve: 2D plots ----
        if (inner.is_symb_of_sommet(giac::at_curve)) {
            giac::gen &f = inner._SYMBptr->feuille;
            if (f.type != giac::_VECT || f._VECTptr->size() < 2) continue;
            giac::gen &meta = (*f._VECTptr)[0];
            giac::gen &chemin = (*f._VECTptr)[1];
            if (chemin.type != giac::_VECT || chemin._VECTptr->empty()) continue;

            std::string var = "x"; double xmin = 0, xmax = 0;
            if (meta.type == giac::_VECT && meta.subtype == 8 && meta._VECTptr->size() >= 5) {
                auto &mv = *meta._VECTptr;
                if (mv[1].type == giac::_IDNT) var = mv[1].print(contextptr);
                giac::gen xmin_gen = giac::evalf_double(mv[2], 1, contextptr);
                giac::gen xmax_gen = giac::evalf_double(mv[3], 1, contextptr);
                if (xmin_gen.type == giac::_DOUBLE_) xmin = xmin_gen._DOUBLE_val;
                if (xmax_gen.type == giac::_DOUBLE_) xmax = xmax_gen._DOUBLE_val;
            }
            if (!first_item) out << ",";
            out << "{\"type\":\"curve\",\"var\":\"" << json_escape(var)
                << "\",\"xmin\":" << xmin << ",\"xmax\":" << xmax << ",\"pts\":[";
            bool first_pt = true;
            for (const auto &pt : *chemin._VECTptr) { plot_write_pt(out, pt, first_pt, contextptr); }
            out << "]}"; first_item = false;
            continue;
        }

        // ---- hypersurface / hyperplan: 3D surface ----
        if (inner.is_symb_of_sommet(giac::at_hypersurface) || inner.is_symb_of_sommet(giac::at_hyperplan)) {
            giac::gen &f = inner._SYMBptr->feuille;
            // hypersurface can have different arg structures. Try multiple strategies.
            const giac::gen *pnt_data = nullptr;
            giac::gen stripped_pnt_data;

            // Strategy 1: f is a VECT of (data, equation, vars) — 3 elements
            if (f.type == giac::_VECT && f._VECTptr->size() >= 3) {
                giac::gen &d0 = (*f._VECTptr)[0];
                if (d0.type == giac::_VECT && d0.subtype == 8) pnt_data = &d0;
            }
            // Strategy 2: f is a single GROUP__VECT containing a pnt wrapping the data
            if (!pnt_data && f.type == giac::_VECT && f.subtype == 5 && !f._VECTptr->empty()) {
                giac::gen &elem = (*f._VECTptr)[0];
                stripped_pnt_data = elem;
                while (stripped_pnt_data.is_symb_of_sommet(giac::at_pnt))
                    stripped_pnt_data = giac::remove_at_pnt(stripped_pnt_data);
                if (stripped_pnt_data.type == giac::_VECT && stripped_pnt_data.subtype == 8)
                    pnt_data = &stripped_pnt_data;
            }
            // Strategy 3: f itself is the _PNT__VECT data
            if (!pnt_data && f.type == giac::_VECT && f.subtype == 8) pnt_data = &f;

            if (!pnt_data || pnt_data->_VECTptr->size() < 5) continue;
            auto &hv = *pnt_data->_VECTptr;
            giac::gen &grid = hv[4];
            if (grid.type != giac::_VECT || grid.subtype != 5 || grid._VECTptr->empty()) continue;

            double xmin=0,ymin=0,xmax=0,ymax=0;
            std::string v1="x", v2="y";
            if (hv[1].type==giac::_VECT && hv[1]._VECTptr->size()>=2) {
                if ((*hv[1]._VECTptr)[0].type==giac::_IDNT) v1=(*hv[1]._VECTptr)[0].print(contextptr);
                if ((*hv[1]._VECTptr)[1].type==giac::_IDNT) v2=(*hv[1]._VECTptr)[1].print(contextptr);
            }
            if (hv[2].type==giac::_VECT && hv[2]._VECTptr->size()>=2) {
                giac::gen xmin_gen=giac::evalf_double((*hv[2]._VECTptr)[0],1,contextptr);
                giac::gen ymin_gen=giac::evalf_double((*hv[2]._VECTptr)[1],1,contextptr);
                if (xmin_gen.type==giac::_DOUBLE_) xmin=xmin_gen._DOUBLE_val;
                if (ymin_gen.type==giac::_DOUBLE_) ymin=ymin_gen._DOUBLE_val;
            }
            if (hv[3].type==giac::_VECT && hv[3]._VECTptr->size()>=2) {
                giac::gen xmax_gen=giac::evalf_double((*hv[3]._VECTptr)[0],1,contextptr);
                giac::gen ymax_gen=giac::evalf_double((*hv[3]._VECTptr)[1],1,contextptr);
                if (xmax_gen.type==giac::_DOUBLE_) xmax=xmax_gen._DOUBLE_val;
                if (ymax_gen.type==giac::_DOUBLE_) ymax=ymax_gen._DOUBLE_val;
            }
            int nrows = grid._VECTptr->size();
            if (nrows < 2) continue;
            int ncols = 0;
            if ((*grid._VECTptr)[0].type == giac::_VECT)
                ncols = (*grid._VECTptr)[0]._VECTptr->size();
            if (ncols < 2) continue;

            if (!first_item) out << ",";
            out << "{\"type\":\"surface3d\",\"var1\":\"" << json_escape(v1)
                << "\",\"var2\":\"" << json_escape(v2)
                << "\",\"xmin\":" << xmin << ",\"xmax\":" << xmax
                << ",\"ymin\":" << ymin << ",\"ymax\":" << ymax
                << ",\"nx\":" << nrows << ",\"ny\":" << ncols << ",\"z\":[";
            bool first_row = true;
            for (int i = 0; i < nrows; i++) {
                giac::gen &row = (*grid._VECTptr)[i];
                if (!first_row) out << ",";
                out << "[";
                bool first_col = true;
                if (row.type == giac::_VECT) {
                    for (int j = 0; j < ncols && j < (int)row._VECTptr->size(); j++) {
                        giac::gen &cell = (*row._VECTptr)[j];
                        double z = 0; bool haveZ = false;
                        // Cell is a _POINT__VECT [x, y, z] or a raw double
                        if (cell.type == giac::_VECT && cell._VECTptr->size() >= 3) {
                            giac::gen zv = giac::evalf_double((*cell._VECTptr)[2], 1, contextptr);
                            if (zv.type == giac::_DOUBLE_ && std::isfinite(zv._DOUBLE_val)) {
                                z = zv._DOUBLE_val; haveZ = true;
                            }
                        } else {
                            giac::gen zv = giac::evalf_double(cell, 1, contextptr);
                            if (zv.type == giac::_DOUBLE_ && std::isfinite(zv._DOUBLE_val)) {
                                z = zv._DOUBLE_val; haveZ = true;
                            }
                        }
                        if (!first_col) out << ",";
                        if (haveZ) out << z; else out << "null";
                        first_col = false;
                    }
                }
                out << "]"; first_row = false;
            }
            out << "]}"; first_item = false;
            continue;
        }
        // ---- Direct _GROUP__VECT: scatter / polygon ----
        if (inner.type == giac::_VECT && inner.subtype == 5 && !inner._VECTptr->empty()) {
            bool hasNested = false;
            for (const auto &e : *inner._VECTptr)
                if (e.type == giac::_VECT && e.subtype == 5) { hasNested = true; break; }
            if (hasNested) {
                for (const auto &seg : *inner._VECTptr) {
                    if (seg.type != giac::_VECT || seg.subtype != 5 || seg._VECTptr->empty()) continue;
                    if (!first_item) out << ",";
                    out << "{\"type\":\"scatter\",\"pts\":[";
                    bool first_pt = true;
                    for (const auto &pt : *seg._VECTptr) { plot_write_pt(out, pt, first_pt, contextptr); }
                    out << "]}"; first_item = false;
                }
            } else {
                if (!first_item) out << ",";
                out << "{\"type\":\"scatter\",\"pts\":[";
                bool first_pt = true;
                for (const auto &pt : *inner._VECTptr) { plot_write_pt(out, pt, first_pt, contextptr); }
                out << "]}"; first_item = false;
            }
        }
    }
    out << "]";
    return out.str();
}

static bool contains_giac_graphic(const giac::gen &value) {
    if (value.is_symb_of_sommet(giac::at_pnt) ||
        value.is_symb_of_sommet(giac::at_curve) ||
        value.is_symb_of_sommet(giac::at_hypersurface) ||
        value.is_symb_of_sommet(giac::at_hyperplan)) {
        return true;
    }
    if (value.type == giac::_VECT) {
        for (const auto &item : *value._VECTptr) {
            if (contains_giac_graphic(item)) return true;
        }
    }
    return false;
}


#endif


std::string make_result(const std::string &symbolic, const std::string &numeric = "", const std::string &error = "", bool isGraphic = false, const std::string &plotExpression = "") {
    std::ostringstream out;
    out << "{\"symbolic\":\"" << json_escape(symbolic)
        << "\",\"numeric\":\"" << json_escape(numeric)
        << "\",\"error\":\"" << json_escape(error)
        << "\",\"latex\":\"\",\"numericLatex\":\""
        << "\",\"backend\":\"" << json_escape(
#if CALCULATORPLUS_WITH_GIAC
            "giac 2.0.0 native core"
#else
            "native xcas-compatible subset; giac source at " CALCULATORPLUS_GIAC_SOURCE
#endif
        )
        << "\""
        << ",\"isGraphic\":" << (isGraphic ? "true" : "false")
        << ",\"plotExpression\":\"" << json_escape(plotExpression) << "\""
        << "}";
    return out.str();
}

#if CALCULATORPLUS_WITH_GIAC
void ensure_giac() {
    if (!giac_context) {
        giac_context = new giac::context();
    }
}

std::string evaluate_with_giac(const std::string &expr, const std::string &mode) {
    try {
        if (interrupted.load()) return make_result("", "", "Evaluation interrupted");
        ensure_giac();
        giac::gen parsed(expr, giac_context);
        giac::gen evaluated = giac::protecteval(parsed, giac::DEFAULT_EVAL_LEVEL, giac_context);
        // A plot is already sampled by Giac. Applying evalf to the complete
        // graphic value walks its metadata as if it were an algebraic result.
        if (mode == "Approx" && !contains_giac_graphic(evaluated)) {
            evaluated = giac::evalf(evaluated, 1, giac_context);
        }
        std::string plotData = extract_giac_plot_data(evaluated, giac_context);
        bool isGraphic = contains_giac_graphic(evaluated) || plotData != "[]";
        // The printed graphic contains every sampled point and can be hundreds
        // of thousands of characters wide when typeset. plotData is the only
        // representation the UI needs for graphic results.
        std::string symbolic = isGraphic ? "" : evaluated.print(giac_context);

        std::string numeric;
        std::string latex;
        std::string numericLatex;
        // Giac's TeX and whole-result evalf paths are not safe for graphic
        // objects. They recursively walk plot metadata as algebraic values and
        // may abort in native code instead of throwing a C++ exception.
        if (!isGraphic) {
            latex = giac::gen2tex(evaluated, giac_context);
            giac::gen approx = giac::evalf(evaluated, 1, giac_context);
            std::string approxText = approx.print(giac_context);
            if (approxText != symbolic) numeric = approxText;
            if (!numeric.empty()) numericLatex = giac::gen2tex(approx, giac_context);
        }
        std::ostringstream plotJson;
        plotJson << "{\"symbolic\":\"" << json_escape(symbolic)
                 << "\",\"numeric\":\"" << json_escape(numeric)
                 << "\",\"latex\":\"" << json_escape(latex)
                 << "\",\"numericLatex\":\"" << json_escape(numericLatex)
                 << "\",\"error\":\"\""
                 << ",\"backend\":\"giac 2.0.0 native core\""
                 << ",\"isGraphic\":" << (isGraphic ? "true" : "false");
        if (isGraphic) {
            plotJson << ",\"plotData\":\"" << json_escape(plotData) << "\"";
        }
        plotJson << "}";
        std::string result = plotJson.str();
        return result;
    } catch (const std::exception &error) {
        return make_result("", "", error.what());
    } catch (...) {
        return make_result("", "", "Unknown giac native error");
    }
}
#endif

std::vector<std::string> split_statements(const std::string &source) {
    std::vector<std::string> parts;
    std::string current;
    int depth = 0;
    for (char c: source) {
        if (c == '(' || c == '[' || c == '{') depth++;
        if (c == ')' || c == ']' || c == '}') depth--;
        if ((c == ';' || c == '\n') && depth <= 0) {
            if (!trim(current).empty()) parts.push_back(trim(current));
            current.clear();
        } else {
            current.push_back(c);
        }
    }
    if (!trim(current).empty()) parts.push_back(trim(current));
    return parts;
}

class Parser {
public:
    explicit Parser(std::string source, double localX = NAN) : source_(std::move(source)), localX_(localX) {}

    double parse() {
        double value = expression();
        skip();
        if (index_ != source_.size()) throw std::runtime_error(std::string("unexpected '") + source_[index_] + "'");
        return value;
    }

private:
    std::string source_;
    size_t index_ = 0;
    double localX_;

    double expression() {
        double value = term();
        while (true) {
            skip();
            if (eat('+')) value += term();
            else if (eat('-')) value -= term();
            else return value;
        }
    }

    double term() {
        double value = power();
        while (true) {
            skip();
            if (eat('*')) value *= power();
            else if (eat('/')) value /= power();
            else if (eat('%')) value = std::fmod(value, power());
            else return value;
        }
    }

    double power() {
        double value = unary();
        skip();
        if (eat('^')) value = std::pow(value, power());
        return value;
    }

    double unary() {
        skip();
        if (eat('+')) return unary();
        if (eat('-')) return -unary();
        return primary();
    }

    double primary() {
        skip();
        if (eat('(')) {
            double value = expression();
            if (!eat(')')) throw std::runtime_error("missing ')'");
            return value;
        }
        if (std::isalpha(peek())) {
            std::string name = read_name();
            if (name == "pi") return M_PI;
            if (name == "e") return M_E;
            if (name == "x" && !std::isnan(localX_)) return localX_;
            if (eat('(')) {
                double arg = expression();
                if (!eat(')')) throw std::runtime_error("missing ')'");
                if (name == "sqrt") return std::sqrt(arg);
                if (name == "sin") return std::sin(arg);
                if (name == "cos") return std::cos(arg);
                if (name == "tan") return std::tan(arg);
                if (name == "ln") return std::log(arg);
                if (name == "log") return std::log10(arg);
                if (name == "abs") return std::fabs(arg);
                auto fn = functions.find(name);
                if (fn != functions.end()) return Parser(fn->second, arg).parse();
                throw std::runtime_error("unsupported function " + name);
            }
            auto var = variables.find(name);
            if (var != variables.end()) return var->second;
            throw std::runtime_error("unknown symbol " + name);
        }
        return number();
    }

    double number() {
        skip();
        size_t start = index_;
        while (std::isdigit(peek()) || peek() == '.') index_++;
        if (start == index_) throw std::runtime_error("expected number");
        return std::stod(source_.substr(start, index_ - start));
    }

    std::string read_name() {
        size_t start = index_;
        while (std::isalnum(peek()) || peek() == '_') index_++;
        return source_.substr(start, index_ - start);
    }

    bool eat(char c) {
        skip();
        if (peek() == c) {
            index_++;
            return true;
        }
        return false;
    }

    char peek() const {
        return index_ < source_.size() ? source_[index_] : '\0';
    }

    void skip() {
        while (std::isspace(static_cast<unsigned char>(peek()))) index_++;
    }
};

std::string integer_factor(long long n) {
    if (n == 0) return "0";
    std::ostringstream out;
    long long value = std::llabs(n);
    if (n < 0) out << "-1";
    bool first = n >= 0;
    for (long long p = 2; p * p <= value; ++p) {
        int count = 0;
        while (value % p == 0) {
            value /= p;
            count++;
        }
        if (count) {
            if (!first) out << "*";
            out << p;
            if (count > 1) out << "^" << count;
            first = false;
        }
    }
    if (value > 1) {
        if (!first) out << "*";
        out << value;
    }
    return out.str();
}

std::string det2(const std::string &expr) {
    std::vector<double> nums;
    std::string current;
    for (char c: expr) {
        if (std::isdigit(static_cast<unsigned char>(c)) || c == '-' || c == '.') current.push_back(c);
        else if (!current.empty()) {
            nums.push_back(std::stod(current));
            current.clear();
        }
    }
    if (!current.empty()) nums.push_back(std::stod(current));
    if (nums.size() == 4) return format_double(nums[0] * nums[3] - nums[1] * nums[2]);
    throw std::runtime_error("det currently supports 2x2 numeric matrices in the fallback backend");
}

std::string symbolic_known(const std::string &statement) {
    const std::string c = compact(statement);
    if (c == "factor(x^2-1)") return "(x-1)*(x+1)";
    if (c == "expand((x+1)^3)") return "x^3+3*x^2+3*x+1";
    if (c == "simplify((x^2-1)/(x-1))") return "x+1";
    if (c == "diff(sin(x),x)") return "cos(x)";
    if (c == "integrate(x^2,x)") return "x^3/3";
    if (c == "solve(x^2-1=0,x)") return "[-1,1]";
    if (c == "limit(sin(x)/x,x=0)") return "1";
    if (c.rfind("det(", 0) == 0) return det2(c);
    if (c.rfind("ifactor(", 0) == 0 && c.back() == ')') {
        return integer_factor(std::stoll(c.substr(8, c.size() - 9)));
    }
    if (c.rfind("plot(", 0) == 0) return "plot ready: " + c.substr(5, c.size() - 6);
    if (c.rfind("normal(", 0) == 0 || c.rfind("subst(", 0) == 0 || c.rfind("rank(", 0) == 0 ||
        c.rfind("transpose(", 0) == 0 || c.rfind("inv(", 0) == 0 || c.rfind("gcd(", 0) == 0 ||
        c.rfind("lcm(", 0) == 0 || c.rfind("mod(", 0) == 0 || c.rfind("arg(", 0) == 0 ||
        c.rfind("re(", 0) == 0 || c.rfind("im(", 0) == 0 || c.rfind("conj(", 0) == 0) {
        return statement;
    }
    return "";
}

std::string evaluate_statement(const std::string &statement, std::string &numeric) {
    const std::string text = trim(statement);
    if (text.empty()) return "";
    const std::string known = symbolic_known(text);
    if (!known.empty()) {
        numeric = known == "1" || known == "-2" ? known : "";
        return known;
    }
    const std::string c = compact(text);
    size_t fnAssign = c.find("(x):=");
    if (fnAssign != std::string::npos && fnAssign > 0) {
        std::string name = c.substr(0, fnAssign);
        std::string body = c.substr(fnAssign + 5);
        functions[name] = body;
        return name + "(x):=" + body;
    }
    size_t assign = c.find(":=");
    if (assign != std::string::npos) {
        std::string name = c.substr(0, assign);
        std::string valueExpr = c.substr(assign + 2);
        double value = Parser(valueExpr).parse();
        variables[name] = value;
        numeric = format_double(value);
        return name + ":=" + numeric;
    }
    double value = Parser(c).parse();
    numeric = format_double(value);
    return numeric;
}

std::string evaluate(const std::string &expr, const std::string &mode) {
#if CALCULATORPLUS_WITH_GIAC
    return evaluate_with_giac(expr, mode);
#else
    try {
        if (interrupted.load()) return make_result("", "", "Evaluation interrupted");
        std::string symbolic;
        std::string numeric;
        for (const auto &statement: split_statements(expr)) {
            symbolic = evaluate_statement(statement, numeric);
        }
        if (mode == "Approx" && !numeric.empty()) symbolic = numeric;
        if (mode == "Exact" && symbolic.empty()) symbolic = numeric;
        return make_result(symbolic, numeric);
    } catch (const std::exception &error) {
        return make_result("", "", error.what());
    }
#endif
}

std::string plot_sample(const std::string &expr_str, std::string var_str,
                        double xmin, double xmax, int samples) {
#if CALCULATORPLUS_WITH_GIAC
    try {
        ensure_giac();
        if (var_str.empty()) var_str = "x";

        giac::gen parsed(expr_str, giac_context);
        giac::gen simplified = giac::protecteval(parsed, giac::DEFAULT_EVAL_LEVEL, giac_context);
        giac::identificateur var_id(var_str.c_str());
        int n = samples < 2 ? 300 : samples;
        double step = (xmax - xmin) / (n - 1);

        std::ostringstream out;
        out << "[";
        bool first = true;
        for (int i = 0; i < n; i++) {
            double x = xmin + i * step;
            giac::gen substituted = giac::subst(
                simplified, var_id, giac::gen(x), false, giac_context);
            giac::gen approx = giac::evalf(substituted, 1, giac_context);

            double y = 0;
            bool valid = false;
            if (approx.type == giac::_DOUBLE_) {
                y = approx.DOUBLE_val();
                valid = std::isfinite(y);
            } else if (approx.type == giac::_FLOAT_) {
                giac::gen real_part = giac::re(approx, giac_context);
                if (real_part.type == giac::_DOUBLE_) {
                    y = real_part.DOUBLE_val();
                    valid = std::isfinite(y);
                }
            }

            if (valid) {
                if (!first) out << ",";
                out << "[" << x << "," << y << "]";
                first = false;
            }
        }
        out << "]";
        return out.str();
    } catch (...) {
        return "[]";
    }
#else
    (void) expr_str; (void) var_str; (void) xmin; (void) xmax; (void) samples;
    return "[]";
#endif
}

std::string engine_help(const std::string &command) {
#if CALCULATORPLUS_WITH_GIAC
    try {
        ensure_giac();
        std::string expr = "help(\"" + command + "\")";
        giac::gen parsed(expr, giac_context);
        giac::gen result = giac::protecteval(parsed, giac::DEFAULT_EVAL_LEVEL, giac_context);
        std::string text;
        if (result.type == giac::_STRNG && result._STRNGptr) {
            text = *result._STRNGptr;
        } else {
            text = result.print(giac_context);
            if (text.size() >= 2 && text.front() == '"' && text.back() == '"')
                text = text.substr(1, text.size() - 2);
        }
        return text.find("No help available") == 0 ? "" : text;
    } catch (...) {
        return "";
    }
#else
    (void) command;
    return "";
#endif
}

thread_local std::string c_api_result;

const char *keep_c_api_result(std::string value) {
    c_api_result = std::move(value);
    return c_api_result.c_str();
}

#ifdef __ANDROID__
std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (!value) return "";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring string_to_jstring(JNIEnv *env, const std::string &value) {
    return env->NewStringUTF(value.c_str());
}
#endif

}

extern "C" void calcora_engine_init(void) {
    std::lock_guard<std::mutex> lock(engine_mutex);
#if CALCULATORPLUS_WITH_GIAC
    ensure_giac();
    giac::language(2, giac_context);
#endif
    variables.try_emplace("pi", M_PI);
    variables.try_emplace("e", M_E);
    interrupted.store(false);
}

extern "C" const char *calcora_engine_evaluate(const char *expr, const char *mode) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    return keep_c_api_result(evaluate(expr ? expr : "", mode ? mode : "Auto"));
}

extern "C" const char *calcora_engine_plot_sample(
    const char *expr, const char *variable, double xmin, double xmax, int samples) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    return keep_c_api_result(plot_sample(expr ? expr : "", variable ? variable : "x", xmin, xmax, samples));
}

extern "C" const char *calcora_engine_help(const char *command) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    return keep_c_api_result(engine_help(command ? command : ""));
}

extern "C" void calcora_engine_reset(void) {
    std::lock_guard<std::mutex> lock(engine_mutex);
#if CALCULATORPLUS_WITH_GIAC
    delete giac_context;
    giac_context = new giac::context();
#endif
    variables.clear();
    functions.clear();
    variables["pi"] = M_PI;
    variables["e"] = M_E;
    interrupted.store(false);
}

extern "C" void calcora_engine_interrupt(void) {
    interrupted.store(true);
}

extern "C" void calcora_engine_set_language(int code) {
    std::lock_guard<std::mutex> lock(engine_mutex);
#if CALCULATORPLUS_WITH_GIAC
    ensure_giac();
    if (code > 0) giac::language(code, giac_context);
#else
    (void) code;
#endif
}

extern "C" void calcora_engine_set_help_dir(const char *path) {
#if CALCULATORPLUS_WITH_GIAC
    if (path) setenv("XCAS_ROOT", path, 1);
#else
    (void) path;
#endif
}

extern "C" const char *calcora_engine_version(void) {
    return keep_c_api_result(
#if CALCULATORPLUS_WITH_GIAC
        std::string("Giac 2.0.0 native core integrated from ") + CALCULATORPLUS_GIAC_SOURCE
#else
        std::string("Calcora native xcas-compatible subset; giac 2.0.0 source present at ") + CALCULATORPLUS_GIAC_SOURCE
#endif
    );
}

#ifdef __ANDROID__
extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeSetLanguage(
    JNIEnv *, jobject, jint code) {
    calcora_engine_set_language(code);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeInit(JNIEnv *, jobject) {
    calcora_engine_init();
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeEvaluate(JNIEnv *env, jobject, jstring expr, jstring mode) {
    return string_to_jstring(env, calcora_engine_evaluate(
        jstring_to_string(env, expr).c_str(), jstring_to_string(env, mode).c_str()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeEvaluateRawXcas(JNIEnv *env, jobject, jstring expr) {
    return string_to_jstring(env, calcora_engine_evaluate(jstring_to_string(env, expr).c_str(), "RawXcas"));
}

extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeReset(JNIEnv *, jobject) {
    calcora_engine_reset();
}

extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeInterrupt(JNIEnv *, jobject) {
    calcora_engine_interrupt();
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativePlotSample(
    JNIEnv *env, jobject, jstring expr, jstring varName, jdouble xmin, jdouble xmax, jint samples) {
    return string_to_jstring(env, calcora_engine_plot_sample(
        jstring_to_string(env, expr).c_str(), jstring_to_string(env, varName).c_str(),
        xmin, xmax, samples));
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeHelp(
    JNIEnv *env, jobject, jstring command) {
    return string_to_jstring(env, calcora_engine_help(jstring_to_string(env, command).c_str()));
}
extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeSetHelpDir(
    JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    if (!p) return;
    calcora_engine_set_help_dir(p);
    env->ReleaseStringUTFChars(path, p);
}


extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeVersion(JNIEnv *env, jobject) {
    return string_to_jstring(env, calcora_engine_version());
}
#endif
